package space.iseki.cmdpipe;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public interface StreamProcessor<T, R> {
    static <R> @NotNull StreamProcessor<@NotNull OutputStream, R> write(@NotNull H<@NotNull OutputStream, R> h) {
        return of(h);
    }

    static <R> @NotNull StreamProcessor<@NotNull InputStream, R> read(@NotNull H<@NotNull InputStream, R> h) {
        return of(h);
    }

    static @NotNull StreamProcessor<@NotNull OutputStream, Void> write(@NotNull HVoid<@NotNull OutputStream> h) {
        return of(h);
    }

    static @NotNull StreamProcessor<@NotNull InputStream, Void> read(@NotNull HVoid<@NotNull InputStream> h) {
        return of(h);
    }

    private static <T> StreamProcessor<T, Void> of(HVoid<T> h) {
        return of((cmd, stdio, stream) -> {
            h.handle(cmd, stdio, stream);
            return null;
        });
    }

    private static <T, R> StreamProcessor<T, R> of(H<T, R> h) {
        return new StreamProcessor<>() {
            private final CompletableFuture<R> future = new CompletableFuture<>();

            @Override
            public @NotNull CompletableFuture<R> future() {
                return future;
            }

            @Override
            public void process(@NotNull Cmd cmd, @NotNull Stdio stdio, T stream) throws Exception {
                try {
                    future.complete(h.handle(cmd, stdio, stream));
                } catch (Throwable th) {
                    future.completeExceptionally(th);
                    throw th;
                }
            }
        };
    }

    void process(@NotNull Cmd cmd, @NotNull Stdio stdio, T stream) throws Exception;

    @NotNull
    CompletableFuture<R> future();

    @FunctionalInterface
    interface HVoid<T> {
        void handle(@NotNull Cmd cmd, @NotNull Stdio stdio, T stream) throws Exception;
    }

    @FunctionalInterface
    interface H<T, R> {
        R handle(@NotNull Cmd cmd, @NotNull Stdio stdio, T stream) throws Exception;
    }
    
}
