package space.iseki.cmdpipe;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

public record CommandInfo(
        @NonNull List<@NonNull String> commandLine,
        @Nullable File workingDirectory,
        @NonNull List<@NonNull EnvVar> additionalEnvVars,
        long timeout,
        boolean inheritIO,
        boolean killSubprocess,
        boolean enableDefaultErrorRecorder,
        @NonNull Charset ioCharset
) {
}


