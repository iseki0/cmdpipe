package space.iseki.cmdpipe;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public interface Cmd {
    /**
     * Create a write-stream processor.
     *
     * @param h   the handler
     * @param <R> the result type
     * @return the stream processor
     */
    static <R> @NotNull StreamProcessor<@NotNull OutputStream, R> output(@NotNull Cmd.StreamProcessor.H<@NotNull OutputStream, R> h) {
        return new StreamProcessorImpl<>(h);
    }

    /**
     * Create a read-stream processor.
     *
     * @param h   the handler
     * @param <R> the result type
     * @return the stream processor
     */
    static <R> @NotNull StreamProcessor<@NotNull InputStream, R> input(@NotNull Cmd.StreamProcessor.H<@NotNull InputStream, R> h) {
        return new StreamProcessorImpl<>(h);
    }

    /**
     * Get the first process of the command.
     *
     * @return the first process of the command
     */
    default @NotNull Process getProcess() {
        return getProcesses().get(0);
    }

    /**
     * Get the processes of the command.
     *
     * @return the processes of the command, the list is unmodifiable
     */
    @NotNull List<@NotNull Process> getProcesses();

    /**
     * Stop all processes of the command.
     *
     * @param force whether to force stop
     */
    void stopAll(boolean force);

    void waitFor() throws InterruptedException;

    boolean waitFor(long timeout, @NotNull TimeUnit unit) throws InterruptedException;


    /**
     * The standard IO streams.
     */
    enum Stdio {
        /**
         * The standard input stream.
         */
        STDIN(0),
        /**
         * The standard output stream.
         */
        STDOUT(1),
        /**
         * The standard error stream.
         */
        STDERR(2),
        ;
        /**
         * The FD of the standard IO stream.
         */
        final int i;

        Stdio(int i) {
            this.i = i;
        }

        /**
         * Whether the stream is readable.
         *
         * @return true if the stream is readable
         */
        public boolean isReadable() {
            return !isWriteable();
        }

        /**
         * Whether the stream is writeable.
         *
         * @return true if the stream is writeable
         */
        public boolean isWriteable() {
            return this == STDIN;
        }
    }

    sealed interface StreamProcessor<T, R> {
//        void process(@NotNull Ctx<@NotNull T> ctx) throws Exception;

        /**
         * Get the future of the stream processor.
         *
         * @return the future of the stream processor
         */
        @NotNull CompletableFuture<R> future();

        @NotNull StreamProcessor<@NotNull T, R> lastInit();

        interface Ctx<T> {
            /**
             * Get the command.
             *
             * @return the command
             */
            @NotNull Cmd cmd();

            /**
             * Get the command.
             *
             * @return the command
             */
            default @NotNull Cmd getCmd() {
                return cmd();
            }

            /**
             * Get the standard IO stream.
             *
             * @return the standard IO stream
             */
            @NotNull Stdio stdio();

            /**
             * Get the standard IO stream.
             *
             * @return the standard IO stream
             */
            default @NotNull Stdio getStdio() {
                return stdio();
            }

            /**
             * Get the stream.
             *
             * @return the stream
             */
            @NotNull T stream();

            /**
             * Get the stream.
             *
             * @return the stream
             */
            default @NotNull T getStream() {
                return stream();
            }

            /**
             * Get the command.
             *
             * @return the command
             */
            default @NotNull Cmd component1() {
                return cmd();
            }

            /**
             * Get the standard IO stream.
             *
             * @return the standard IO stream
             */
            default @NotNull Stdio component2() {
                return stdio();
            }

            /**
             * Get the stream.
             *
             * @return the stream
             */
            default @NotNull T component3() {
                return stream();
            }
        }

        @FunctionalInterface
        interface H<T, R> {
            R handle(@NotNull Ctx<T> ctx) throws Exception;
        }

    }

    class Builder {
        private final HashSet<String> confidentialEnvNames = new HashSet<>();
        private final HashMap<String, String> envs = new HashMap<>();
        private final Executor executor = ForkJoinPool.commonPool();
        private byte inheritIO = 0;
        private String[] cmds;
        private File workingDir;
        private StreamProcessorImpl<InputStream, ?> stdoutProcessor;
        private StreamProcessorImpl<InputStream, ?> stderrProcessor;
        private ProcessBuilder[] pbs;
        private boolean autoGrantExecutablePerm = false;

        static void closeIgnoreIOException(Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }

        static void killTree(Process process, boolean force) {
            try {
                process.descendants().forEach(p -> killTree(p, force));
            } catch (UnsupportedOperationException ignored) {
            }
            if (force) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
        }

        static void killTree(ProcessHandle ph, boolean force) {
            ph.descendants().forEach(p -> killTree(p, force));
            kill(ph, force);
        }

        static void kill(ProcessHandle ph, boolean force) {
            if (force) {
                ph.destroyForcibly();
            } else {
                ph.destroy();
            }
        }

        static boolean isPermissionProblem(IOException e) {
            return OSNameUtils.IS_UNIX_LIKE && Optional.ofNullable(e.getMessage()).map(s -> s.contains("error=13")).orElse(false);
        }

        static boolean tryToGrantExecutable(ProcessBuilder[] pbs) {
            return Arrays.stream(pbs).map(p -> tryToGrantExecutable(p.command().get(0))).reduce(false, (a, b) -> a || b);
        }

        static boolean tryToGrantExecutable(String name) {
            try {
                var p = Path.of(name);
                if (!Files.isRegularFile(p) || Files.isExecutable(p)) return false;
                return p.toFile().setExecutable(true);
            } catch (Exception e) {
                return false;
            }
        }

        static List<Process> start(ProcessBuilder[] pbs) throws IOException {
            return pbs.length == 1 ? List.of(pbs[0].start()) : Collections.unmodifiableList(ProcessBuilder.startPipeline(List.of(pbs)));
        }

        static List<Process> startRetryIfFailed(ProcessBuilder[] pbs) throws IOException {
            try {
                return start(pbs);
            } catch (IOException e) {
                if (isPermissionProblem(e) && tryToGrantExecutable(pbs)) {
                    return start(pbs);
                } else {
                    throw e;
                }
            }
        }

        private static <T> T[] nonNullInArray(T[] arr) {
            for (int i = 0; i < arr.length; i++) {
                int finalI = i;
                Objects.requireNonNull(arr[i], () -> "Element at index " + finalI + " is null");
            }
            return arr;
        }

        /**
         * Auto grant executable permission on failure if required.
         *
         * @param enable enable it?
         * @return this, so that the method can be chained
         * @see File#setExecutable(boolean)
         */
        public @NotNull Builder autoGrantExecutableOnFailure(boolean enable) {
            autoGrantExecutablePerm = enable;
            return this;
        }

        /**
         * Auto grant executable permission on failure if required.
         *
         * @return this, so that the method can be chained
         * @see File#setExecutable(boolean)
         */
        public @NotNull Builder autoGrantExecutableOnFailure() {
            return autoGrantExecutableOnFailure(true);
        }

        private boolean configureRedirect(ProcessBuilder pb, Stdio stdio, boolean processorSet) {
            boolean inherit = (inheritIO & 1 << stdio.i) > 0;
            var redirect = inherit ? ProcessBuilder.Redirect.INHERIT : switch (stdio) {
                case STDERR, STDOUT -> processorSet ? ProcessBuilder.Redirect.PIPE : ProcessBuilder.Redirect.DISCARD;
                case STDIN -> ProcessBuilder.Redirect.PIPE;
            };
            switch (stdio) {
                case STDIN -> pb.redirectInput(redirect);
                case STDOUT -> pb.redirectOutput(redirect);
                case STDERR -> pb.redirectError(redirect);
            }
            return redirect == ProcessBuilder.Redirect.PIPE;
        }

        /**
         * Set the working directory for the command.
         *
         * @param dir the working directory for the command, or null to use the current working directory
         * @return this, so that the method can be chained
         * @see ProcessBuilder#directory(File)
         */
        public @NotNull Builder workingDir(@Nullable File dir) {
            this.workingDir = dir;
            return this;
        }

        /**
         * Set the working directory for the command.
         *
         * @param dir the working directory for the command, or null to use the current working directory
         * @return this, so that the method can be chained
         * @throws UnsupportedOperationException if this Path is not associated with the default provider
         * @see ProcessBuilder#directory(File)
         * @see Path#toFile()
         */
        public @NotNull Builder workingDir(@Nullable Path dir) {
            return workingDir(dir != null ? dir.toFile() : null);
        }

        /**
         * Set the IO as {@link ProcessBuilder.Redirect#INHERIT} for the specified stdio.
         *
         * @param stdio the {@link Stdio}
         * @return this, so that the method can be chained
         * @throws NullPointerException if stdio is null
         */
        public @NotNull Builder inheritIO(@NotNull Stdio stdio) {
            return inheritIO(stdio, true);
        }

        /**
         * Set the IO as {@link ProcessBuilder.Redirect#INHERIT} or not for the specified stdio.
         *
         * @param stdio   the {@link Stdio}
         * @param inherit whether to inherit the IO
         * @return this, so that the method can be chained
         * @throws NullPointerException if stdio is null
         */
        public @NotNull Builder inheritIO(@NotNull Stdio stdio, boolean inherit) {
            if (inherit) {
                inheritIO = (byte) (inheritIO | 1 << stdio.i);
            } else {
                inheritIO = (byte) (inheritIO & ~(1 << stdio.i));
            }
            return this;
        }

        /**
         * Append an environment variable to the command's environment.
         *
         * @param name  the name of the environment variable
         * @param value the value of the environment variable, or null to unset it
         * @return this, so that the method can be chained
         * @throws NullPointerException if name is null
         * @see ProcessBuilder#environment()
         */
        @SuppressWarnings("UnusedReturnValue")
        public @NotNull Builder env(@NotNull String name, @Nullable String value) {
            envs.put(Objects.requireNonNull(name), value);
            return this;
        }

        /**
         * Append an environment variable to the command's environment.
         *
         * @param name         the name of the environment variable
         * @param value        the value of the environment variable, or null to unset it
         * @param confidential whether the environment variable is confidential
         * @return this, so that the method can be chained
         * @throws NullPointerException if name is null
         * @see ProcessBuilder#environment()
         */
        public @NotNull Builder env(@NotNull String name, @Nullable String value, boolean confidential) {
            env(name, value);
            if (confidential) {
                markEnvConfidential(name);
            }
            return this;
        }

        /**
         * Mark an environment variable as confidential.
         *
         * @param name the name of the environment variable
         * @return this, so that the method can be chained
         * @throws NullPointerException if name is null
         */
        @SuppressWarnings("UnusedReturnValue")
        public @NotNull Builder markEnvConfidential(@NotNull String name) {
            confidentialEnvNames.add(Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set the command to run and its arguments.
         *
         * @param cmds the command and its arguments
         * @return this, so that the method can be chained
         * @throws NullPointerException      if any of the elements in cmds is null
         * @throws IndexOutOfBoundsException if cmds is empty
         */
        public @NotNull Builder cmdline(@NotNull String... cmds) {
            if (cmds.length == 0) throw new IndexOutOfBoundsException("cmds is empty");
            this.cmds = nonNullInArray(Arrays.copyOf(cmds, cmds.length));
            return this;
        }

        /**
         * Set the command to run and its arguments.
         *
         * @param cmds the command and its arguments
         * @return this, so that the method can be chained
         * @throws NullPointerException      if any of the elements in cmds is null
         * @throws IndexOutOfBoundsException if cmds is empty
         */
        public @NotNull Builder cmdline(@NotNull Collection<@NotNull String> cmds) {
            var a = cmds.toArray(String[]::new);
            if (a.length == 0) throw new IndexOutOfBoundsException("cmds is empty");
            this.cmds = nonNullInArray(a);
            return this;
        }

        /**
         * Set the processor for the command's stdout.
         *
         * @param processor the processor for the command's stdout
         * @return this, so that the method can be chained
         * @throws NullPointerException if processor is null
         * @see #input(StreamProcessor.H)
         */
        public @NotNull Builder handleStdout(@NotNull StreamProcessor<InputStream, ?> processor) {
            inheritIO(Stdio.STDOUT, false);
            this.stdoutProcessor = (StreamProcessorImpl<InputStream, ?>) Objects.requireNonNull(processor);
            return this;
        }

        /**
         * Set the processor for the command's stderr.
         *
         * @param processor the processor for the command's stderr
         * @return this, so that the method can be chained
         * @throws NullPointerException if processor is null
         * @see #input(StreamProcessor.H)
         */
        public @NotNull Builder handleStderr(@NotNull StreamProcessor<InputStream, ?> processor) {
            inheritIO(Stdio.STDERR, false);
            this.stderrProcessor = (StreamProcessorImpl<InputStream, ?>) Objects.requireNonNull(processor);
            return this;
        }

        /**
         * Start the command.
         *
         * @return the started command
         * @throws IndexOutOfBoundsException                       if neither cmdline nor {@link ProcessBuilder} was set
         * @throws IOException                                     if an I/O error occurs
         * @throws UnsupportedOperationException                   if the operating system does not support the creation of processes
         * @throws IllegalStateException                           if any processor has been re-used(which is not allowed), such as {@link Builder#handleStderr(StreamProcessor)}, {@link Builder#handleStdout(StreamProcessor)}
         * @throws java.util.concurrent.RejectedExecutionException if the processor task cannot be scheduled for execution, see also: {@link Executor#execute(Runnable)}
         * @see ProcessBuilder#start()
         */
        public @NotNull Cmd start() throws IOException {
            CmdImpl cmd = null;
            try {
                var pbs = getPbs();
                var lastPb = pbs[pbs.length - 1];
                var firstPb = pbs[0];
                var stdoutStart = configureRedirect(lastPb, Stdio.STDOUT, stdoutProcessor != null);
                var stderrStart = configureRedirect(lastPb, Stdio.STDERR, stderrProcessor != null);
                for (ProcessBuilder pb : pbs) configureEnvAndDir(pb);
                var processes = autoGrantExecutablePerm ? startRetryIfFailed(pbs) : start(pbs);
                cmd = new CmdImpl(processes);
                var lastProcess = processes.get(processes.size() - 1);
                if (stdoutStart) {
                    cmd.startHandler(executor, Stdio.STDOUT, stdoutProcessor, lastProcess.getInputStream());
                }
                if (stderrStart) {
                    cmd.startHandler(executor, Stdio.STDERR, stderrProcessor, lastProcess.getErrorStream());
                }
                return cmd;
            } catch (Throwable th) {
                var spThrows = new RuntimeException("command start failed", th);
                Optional.ofNullable(stdoutProcessor).ifPresent(c -> c.markFailed(spThrows));
                Optional.ofNullable(stderrProcessor).ifPresent(c -> c.markFailed(spThrows));
                if (cmd != null) {
                    cmd.stopAll(true);
                }
                throw th;
            }
        }

        private void configureEnvAndDir(ProcessBuilder pb) {
            if (!envs.isEmpty()) {
                pb.environment().putAll(envs);
            }
            if (workingDir != null) {
                pb.directory(workingDir);
            }
        }

        private ProcessBuilder[] getPbs() {
            var pbsConfigured = pbs != null && pbs.length > 0;
            var cmdConfigured = cmds != null && cmds.length > 0;
            if (!pbsConfigured && !cmdConfigured) {
                throw new IndexOutOfBoundsException("cmdline not set");
            }
            return pbsConfigured ? pbs : new ProcessBuilder[]{new ProcessBuilder(cmds)};
        }

    }

}

class CmdImpl implements Cmd {
    private final List<Process> processes;

    CmdImpl(List<Process> processes) {
        this.processes = processes;
    }

    @Override
    public @NotNull List<@NotNull Process> getProcesses() {
        return processes;
    }

    <T> void startHandler(Executor executor, Stdio stdio, StreamProcessorImpl<T, ?> p, T stream) {
        Objects.requireNonNull(stream, () -> "stream is null for " + stdio);
        p.process(executor, new StreamProcessorCtxRecord<>(this, stdio, stream), th -> stopAll(true));
    }

    @Override
    public void stopAll(boolean force) {
        var ps = getProcesses();
        getProcesses().forEach(p -> Builder.killTree(p, force));
        if (force) {
            Optional.ofNullable(ps.get(0).getInputStream()).ifPresent(Builder::closeIgnoreIOException);
            Optional.ofNullable(ps.get(ps.size() - 1).getOutputStream()).ifPresent(Builder::closeIgnoreIOException);
            Optional.ofNullable(ps.get(ps.size() - 1).getErrorStream()).ifPresent(Builder::closeIgnoreIOException);
        }
    }

    @Override
    public void waitFor() throws InterruptedException {
        for (Process process : processes) {
            process.waitFor();
        }
    }

    @Override
    public boolean waitFor(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        if (processes.size() == 1) return processes.get(0).waitFor(timeout, unit);
        long now = System.currentTimeMillis();
        long end = now + unit.toMillis(timeout);
        if (end < now) return false;
        for (Process p : processes) {
            long left = end - System.currentTimeMillis();
            if (left <= 0) return false;
            if (!p.waitFor(left, TimeUnit.MILLISECONDS)) return false;
        }
        return true;
    }
}

record StreamProcessorCtxRecord<T>(Cmd cmd, Cmd.Stdio stdio, T stream) implements Cmd.StreamProcessor.Ctx<T> {
}

final class StreamProcessorImpl<T, R> implements Cmd.StreamProcessor<T, R> {
    private static final VarHandle FUTURE;
    private static final VarHandle ALREADY_USED;

    static {
        try {
            var lookup = MethodHandles.lookup();
            FUTURE = lookup.findVarHandle(StreamProcessorImpl.class, "future", CompletableFuture.class).withInvokeExactBehavior();
            ALREADY_USED = lookup.findVarHandle(StreamProcessorImpl.class, "alreadyUsed", boolean.class).withInvokeExactBehavior();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final H<T, R> h;
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private boolean alreadyUsed = false;
    @SuppressWarnings("unused")
    private volatile CompletableFuture<R> future;

    public StreamProcessorImpl(H<T, R> h) {
        this.h = h;
    }

    public void process(Executor executor, Ctx<T> ctx, Consumer<Throwable> failCallback) {
        if (!ALREADY_USED.compareAndSet(this, false, true)) {
            throw new IllegalStateException("processor already been used, " + ctx.stdio());
        }
        CompletableFuture<R> future = new CompletableFuture<>();
        //noinspection unchecked;
        CompletableFuture<R> old = (CompletableFuture<R>) FUTURE.compareAndExchange(this, (CompletableFuture<R>) null, future);
        if (old != null) {
            future = old;
        }
        CompletableFuture<R> finalFuture = future;
        executor.execute(() -> {
            try {
                finalFuture.complete(h.handle(ctx));
            } catch (Throwable th) {
                finalFuture.completeExceptionally(th);
                failCallback.accept(th);
            }
        });
    }

    public void markFailed(Throwable th) {
        Optional.ofNullable((CompletableFuture<?>) FUTURE.compareAndExchange(this, (CompletableFuture<?>) null, CompletableFuture.failedFuture(th))).ifPresent(f -> f.completeExceptionally(th));
    }

    @Override
    public @NotNull CompletableFuture<R> future() {
        //noinspection unchecked
        return Optional.ofNullable((CompletableFuture<R>) FUTURE.getAcquire(this)).orElseThrow(() -> new IllegalStateException("processor not ready"));
    }

    @Override
    public @NotNull Cmd.StreamProcessor<@NotNull T, R> lastInit() {
        FUTURE.compareAndSet(this, (CompletableFuture<?>) null, new CompletableFuture<>());
        return this;
    }
}

class OSNameUtils {
    private static final String OS_NAME = System.getProperty("os.name", "");
    private static final boolean IS_LINUX = match("Linux");
    private static final boolean IS_MAC = match("Mac");
    private static final boolean IS_BSD_LIKE = match("FreeBSD") || match("NetBSD") || match("OpenBSD");
    public static final boolean IS_UNIX_LIKE = IS_LINUX || IS_MAC || IS_BSD_LIKE;

    static boolean match(final String prefix) {
        if (OS_NAME == null) {
            return false;
        }
        return OS_NAME.startsWith(prefix);
    }
}
