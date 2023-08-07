package space.iseki.cmdpipe;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Instant;

public record ExecutionInfo(
        long pid,
        @NonNull Instant startAt,
        @Nullable Instant endAt,
        @Nullable Integer exitCode,
        boolean timeoutToKilled,
        @NonNull String stderrSnapshot
){}

