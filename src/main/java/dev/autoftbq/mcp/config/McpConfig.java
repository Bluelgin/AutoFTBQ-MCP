package dev.autoftbq.mcp.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.platform.GamePlatform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public record McpConfig(boolean enabled, String bindAddress, int port, boolean allowRemote,
                        int maxBodyBytes, boolean writeRuntimeCredentials,
                        boolean allowCommandRewards) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );
    private static final Set<PosixFilePermission> PRIVATE_FILE = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );

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
        if (!writeRuntimeCredentials) {
            clearRuntime();
            return;
        }

        Path target = runtimePath();
        Path directory = target.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            setPermissions(directory, PRIVATE_DIRECTORY);

            JsonObject value = new JsonObject();
            value.addProperty("url", "http://" + bindAddress + ":" + actualPort + "/mcp");
            value.addProperty("token", token);
            value.addProperty("protocolVersion", protocolVersion);
            value.addProperty("note", "Local credential file. Do not publish or commit this token.");

            temporary = Files.createTempFile(directory, "runtime-", ".json.tmp");
            Files.writeString(temporary, GSON.toJson(value), StandardCharsets.UTF_8);
            setPermissions(temporary, PRIVATE_FILE);

            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setPermissions(target, PRIVATE_FILE);
        } catch (IOException ignored) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignoredAgain) {
                }
            }
        }
    }

    public static void clearRuntime() {
        try {
            Files.deleteIfExists(runtimePath());
        } catch (IOException ignored) {
        }
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows and non-POSIX providers do not support POSIX mode bits.
        }
    }
}
