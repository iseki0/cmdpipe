package space.iseki.cmdpipe;

import kotlin.Pair;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public record EnvVar(
        @NonNull String name,
        @Nullable String value,
        boolean confidential
) {
    public EnvVar(Pair<String, String> pair) {
        this(pair.getFirst(), pair.getSecond(), false);
    }

    public EnvVar(@NonNull String name, @Nullable String value) {
        this(name, value, false);
    }

    @Override
    public String toString() {
        return EnvVarFormatter.INSTANCE.format(this);
    }
}
