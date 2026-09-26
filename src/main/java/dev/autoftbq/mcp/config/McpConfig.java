package dev.autoftbq.mcp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.platform.GamePlatform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record McpConfig(boolean enabled, String bindAddress, int port, boolean allowRemote,
                        int maxBodyBytes, boolean writeRuntimeCredentials,
                        boolean allowCommandRewards) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static McpConfig load() {
        Path path = configPath();
        McpConfig defaults = new McpConfig(
                true, "127.0.0.1", 25598, false,
                1_048_576, true, false
        );
        if (!Files.exists(path)) {
            save(defaults);
            return defaults;
        }
        try {
            McpConfig value = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), McpConfig.class);
            if (value == null) return defaults;
            String bind = value.bindAddress == null || value.bindAddress.isBlank()
                    ? defaults.bindAddress : value.bindAddress;
            int port = Math.max(0, Math.min(65535, value.port));
            int maxBody = Math.max(64 * 1024, Math.min(8 * 1024 * 1024, value.maxBodyBytes));
            return new McpConfig(
                    value.enabled,
                    bind,
                    port,
                    value.allowRemote,
                    maxBody,
                    value.writeRuntimeCredentials,
                    value.allowCommandRewards
            );
        } catch (Exception error) {
            return defaults;
        }
    }

    private static void save(McpConfig value) {
        try {
            Files.createDirectories(configPath().getParent());
            Files.writeString(configPath(), GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static Path configPath() {
        return GamePlatform.environment().configDirectory().resolve("autoftbq-mcp.json");
    }

    public static Path runtimePath() {
        return GamePlatform.environment().configDirectory()
                .resolve("autoftbq-mcp")
                .resolve("runtime.json");
    }

    public void writeRuntime(int actualPort, String token, String protocolVersion) {
        if (!writeRuntimeCredentials) return;
        try {
            Files.createDirectories(runtimePath().getParent());
            JsonObject value = new JsonObject();
            value.addProperty("url", "http://" + bindAddress + ":" + actualPort + "/mcp");
            value.addProperty("token", token);
            value.addProperty("protocolVersion", protocolVersion);
            value.addProperty("note", "Local credential file. Do not publish or commit this token.");
            Files.writeString(runtimePath(), GSON.toJson(value), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
