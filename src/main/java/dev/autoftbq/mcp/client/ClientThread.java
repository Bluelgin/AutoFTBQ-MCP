package dev.autoftbq.mcp.client;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ClientThread {
    private ClientThread() {}

    public static <T> T call(Supplier<T> supplier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) return supplier.get();
        CompletableFuture<T> future = new CompletableFuture<>();
        minecraft.execute(() -> {
            try { future.complete(supplier.get()); }
            catch (Throwable error) { future.completeExceptionally(error); }
        });
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("Minecraft client thread did not complete the MCP operation", error);
        }
    }
}
