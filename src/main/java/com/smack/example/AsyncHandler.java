package com.smack.example;

import java.util.concurrent.*;

public class AsyncHandler {
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public AsyncHandler() {
    }

    public static <T> CompletableFuture<T> run(Callable<T> callable) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                result.complete(callable.call());
            } catch (Throwable var3) {
                result.completeExceptionally(var3);
            }

        }, executor);
        return result;
    }

    public static void run(Runnable runnable) {
        CompletableFuture.runAsync(runnable);
    }

    public static <T> CompletableFuture<T> runWithCallback(Callable<T> callable, AsyncCallbackHandler<T> callback) {
        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            T _result = null;
            Throwable _exception = null;
            try {
                _result = callable.call();
                result.complete(_result);
            } catch (Throwable var3) {
                _exception = var3;
                result.completeExceptionally(var3);
            }

            if (callback != null)
                callback.call(_result, _exception);

        }, executor);
        return result;
    }

    private static int getCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static ScheduledExecutorService defaultExecutorService() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(getCpuCount());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown(scheduledExecutorService);
        }));
        return scheduledExecutorService;
    }

    private static void shutdown(ExecutorService executorService) {
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(60L, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60L, TimeUnit.SECONDS)) {
                    System.err.println("Thread pool did not terminate");
                }
            }
        } catch (InterruptedException var2) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown(executor);
        }));
    }
}
