package space.iseki.cmdpipe;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

@SuppressWarnings("unused")
public interface Cmd extends AutoCloseable {
    static <R> @NotNull StreamProcessor<@NotNull OutputStream, R> write(@NotNull Cmd.StreamProcessor.H<@NotNull OutputStream, R> h) {
        return new StreamProcessorImpl<>(h);
    }

    static <R> @NotNull StreamProcessor<@NotNull InputStream, R> read(@NotNull Cmd.StreamProcessor.H<@NotNull InputStream, R> h) {
        return new StreamProcessorImpl<>(h);
    }


    @Override
    default void close() {
        stopAll(true);
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

    default void stopAll(boolean force) {
        var ps = getProcesses();
        getProcesses().forEach(p -> Builder.killTree(p, force));
        if (force) {
            Builder.closeIgnoreIOException(ps.get(0).getInputStream());
            Builder.closeIgnoreIOException(ps.get(ps.size() - 1).getOutputStream());
            Builder.closeIgnoreIOException(ps.get(ps.size() - 1).getErrorStream());
        }
    }


    enum Stdio {
        STDIN(0), STDOUT(1), STDERR(2);
        final int i;

        Stdio(int i) {
            this.i = i;
        }

        public boolean isReadable() {
            return !isWriteable();
        }

        public boolean isWriteable() {
            return this == STDIN;
        }
    }

    interface StreamProcessor<T, R> {
        void process(@NotNull Ctx<T> ctx) throws Exception;

        @NotNull CompletableFuture<R> future();

        interface Ctx<T> {
            @NotNull Cmd cmd();

            default @NotNull Cmd getCmd() {
                return cmd();
            }

            @NotNull Stdio stdio();

            default @NotNull Stdio getStdio() {
                return stdio();
            }

            @NotNull T stream();

            default @NotNull T getStream() {
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
        private StreamProcessor<InputStream, ?> stdoutProcessor;
        private StreamProcessor<InputStream, ?> stderrProcessor;
        private ProcessBuilder[] pbs;

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

        private boolean configureRedirect(ProcessBuilder pb, Stdio stdio, StreamProcessor<?, ?> sp) {
            boolean inherit = (inheritIO & 1 << stdio.i) > 0;
            var redirect = inherit ? ProcessBuilder.Redirect.INHERIT : switch (stdio) {
                case STDERR, STDOUT -> sp == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.PIPE;
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
            for (var i : cmds) Objects.requireNonNull(i);
            this.cmds = Arrays.copyOf(cmds, cmds.length);
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
            for (var i : a) Objects.requireNonNull(i);
            this.cmds = a;
            return this;
        }

        /**
         * Set the processor for the command's stdout.
         *
         * @param processor the processor for the command's stdout
         * @return this, so that the method can be chained
         * @throws NullPointerException if processor is null
         * @see #read(StreamProcessor.H)
         */
        public @NotNull Builder handleStdout(@NotNull StreamProcessor<InputStream, ?> processor) {
            inheritIO(Stdio.STDOUT, false);
            this.stdoutProcessor = Objects.requireNonNull(processor);
            return this;
        }

        /**
         * Set the processor for the command's stderr.
         *
         * @param processor the processor for the command's stderr
         * @return this, so that the method can be chained
         * @throws NullPointerException if processor is null
         * @see #read(StreamProcessor.H)
         */
        public @NotNull Builder handleStderr(@NotNull StreamProcessor<InputStream, ?> processor) {
            inheritIO(Stdio.STDERR, false);
            this.stderrProcessor = Objects.requireNonNull(processor);
            return this;
        }

        /**
         * Start the command.
         *
         * @return the started command
         * @throws IndexOutOfBoundsException     if neither cmdline nor {@link ProcessBuilder} was set
         * @throws IOException                   if an I/O error occurs
         * @throws UnsupportedOperationException if the operating system does not support the creation of processes
         * @see ProcessBuilder#start()
         */
        public @NotNull Cmd start() throws IOException {
            var pbs = getPbs();
            var lastPb = pbs[pbs.length - 1];
            var firstPb = pbs[0];
            var stdoutStart = configureRedirect(lastPb, Stdio.STDOUT, stdoutProcessor);
            var stderrStart = configureRedirect(lastPb, Stdio.STDERR, stderrProcessor);
            for (ProcessBuilder pb : pbs) {
                if (!envs.isEmpty()) {
                    pb.environment().putAll(envs);
                }
                if (workingDir != null) {
                    pb.directory(workingDir);
                }
            }
            var processes = pbs.length == 1 ? List.of(pbs[0].start()) : ProcessBuilder.startPipeline(List.of(pbs));
            var cmd = new Cmd() {

                @Override
                public @NotNull List<@NotNull Process> getProcesses() {
                    return processes;
                }

                <T> void startHandler(Stdio stdio, StreamProcessor<T, ?> p, T stream) {
                    Objects.requireNonNull(p);
                    executor.execute(() -> {
                        try {
                            p.process(new StreamProcessorCtxRecord<>(this, stdio, stream));
                        } catch (Throwable th) {
                            stopAll(true);
                        }
                    });
                }
            };
            try {
                var lastProcess = processes.get(processes.size() - 1);
                if (stdoutStart) {
                    cmd.startHandler(Stdio.STDOUT, stdoutProcessor, Objects.requireNonNull(lastProcess.getInputStream(), "stdoutProcessor is set but getInputStream() returns null"));
                }
                if (stderrStart) {
                    cmd.startHandler(Stdio.STDERR, stderrProcessor, Objects.requireNonNull(lastProcess.getErrorStream(), "stderrProcessor is set but getErrorStream() returns null"));
                }
            } catch (Throwable th) {
                cmd.stopAll(true);
                throw th;
            }
            return cmd;
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

record StreamProcessorCtxRecord<T>(Cmd cmd, Cmd.Stdio stdio, T stream) implements Cmd.StreamProcessor.Ctx<T> {
}

class StreamProcessorImpl<T, R> implements Cmd.StreamProcessor<T, R> {
    private final H<T, R> h;
    private final CompletableFuture<R> future = new CompletableFuture<>();

    public StreamProcessorImpl(H<T, R> h) {
        this.h = h;
    }

    @Override
    public void process(@NotNull Ctx<T> ctx) throws Exception {
        try {
            future.complete(h.handle(ctx));
        } catch (Throwable th) {
            future.completeExceptionally(th);
            throw th;
        }
    }

    @Override
    public @NotNull CompletableFuture<R> future() {
        return future;
    }
}
