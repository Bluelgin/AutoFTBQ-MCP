package dev.autoftbq.mcp.mcp;

import dev.autoftbq.mcp.config.McpConfig;

public final class McpRuntime {
    private static volatile McpHttpServer server;

    private McpRuntime() {}

    public static synchronized void start() {
        if (server != null) return;
        McpConfig config = McpConfig.load();
        if (!config.enabled()) return;
        McpHttpServer created = new McpHttpServer(config, new McpToolService());
        created.start();
        server = created;
        config.writeRuntime(created.port(), created.token(), McpHttpServer.PROTOCOL_VERSION);
    }

    public static synchronized void stop() {
        McpHttpServer current = server;
        server = null;
        if (current != null) current.stop();
    }
}
