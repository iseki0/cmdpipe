package space.iseki.cmdpipe;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

public interface Cmd {

    class Builder {
        private final HashSet<String> confidentialEnvNames = new HashSet<>();
        private final HashMap<String, String> envs = new HashMap<>();
        private String[] cmds;
        private File workingDir;
        private StreamProcessor<InputStream, ?> stdoutProcessor;
        private StreamProcessor<InputStream, ?> stderrProcessor;

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
         * Append an environment variable to the command's environment.
         *
         * @param name  the name of the environment variable
         * @param value the value of the environment variable, or null to unset it
         * @return this, so that the method can be chained
         * @throws NullPointerException if name is null
         * @see ProcessBuilder#environment()
         */
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
        public @NotNull Builder markEnvConfidential(@NotNull String name) {
            confidentialEnvNames.add(Objects.requireNonNull(name));
            return this;
        }

        /**
         * Set the command to run and its arguments.
         *
         * @param cmds the command and its arguments
         * @return this, so that the method can be chained
         * @throws NullPointerException if any of the elements in cmds is null
         */
        public @NotNull Builder cmdline(@NotNull String... cmds) {
            for (var i : cmds) Objects.requireNonNull(i);
            this.cmds = Arrays.copyOf(cmds, cmds.length);
            return this;
        }

        /**
         * Set the command to run and its arguments.
         *
         * @param cmds the command and its arguments
         * @return this, so that the method can be chained
         * @throws NullPointerException if any of the elements in cmds is null
         */
        public @NotNull Builder cmdline(@NotNull Collection<@NotNull String> cmds) {
            var a = cmds.toArray(String[]::new);
            for (var i : a) Objects.requireNonNull(i);
            this.cmds = a;
            return this;
        }

        /**
         * Set the processor for the command's stdout.
         *
         * @param processor the processor for the command's stdout
         * @return this, so that the method can be chained
         * @see StreamProcessor#read(StreamProcessor.H)
         */
        public @NotNull Builder handleStdout(@NotNull StreamProcessor<InputStream, ?> processor) {
            this.stdoutProcessor = processor;
            return this;
        }

        /**
         * Set the processor for the command's stderr.
         *
         * @param processor the processor for the command's stderr
         * @return this, so that the method can be chained
         * @see StreamProcessor#read(StreamProcessor.H)
         */
        public @NotNull Builder handleStderr(@NotNull StreamProcessor<InputStream, ?> processor) {
            this.stderrProcessor = processor;
            return this;
        }

        public @NotNull Cmd build() {
            throw new UnsupportedOperationException("Not implemented"); // todo
        }
    }
}
