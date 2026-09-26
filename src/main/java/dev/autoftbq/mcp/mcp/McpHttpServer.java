package dev.autoftbq.mcp.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.autoftbq.mcp.AutoFTBQMcpMod;
import dev.autoftbq.mcp.config.McpConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.Executors;

public final class McpHttpServer {
    public static final String PROTOCOL_VERSION = "2025-06-18";
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of(
            "2025-06-18", "2025-03-26", "2024-11-05"
    );
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final McpConfig config;
    private final McpToolService tools;
    private final String token;
    private HttpServer server;

    public McpHttpServer(McpConfig config, McpToolService tools) {
        this.config = config;
        this.tools = tools;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    public void start() {
        try {
            InetAddress bind = InetAddress.getByName(config.bindAddress());
            if (!config.allowRemote() && !bind.isLoopbackAddress()) {
                throw new IllegalArgumentException("allowRemote=false requires a loopback bind address");
            }
            server = HttpServer.create(new InetSocketAddress(bind, config.port()), 0);
            server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
                Thread thread = new Thread(runnable, "AutoFTBQ-MCP-HTTP");
                thread.setDaemon(true);
                return thread;
            }));
            server.createContext("/health", this::health);
            server.createContext("/mcp", this::mcp);
            server.start();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to start AutoFTBQ MCP HTTP server", error);
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    public int port() {
        return server == null ? config.port() : server.getAddress().getPort();
    }

    public String token() {
        return token;
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeStatus(exchange, 405, "");
            return;
        }
        JsonObject value = new JsonObject();
        value.addProperty("ok", true);
        value.addProperty("name", "AutoFTBQ-MCP");
        value.addProperty("version", AutoFTBQMcpMod.VERSION);
        value.addProperty("protocolVersion", PROTOCOL_VERSION);
        writeJson(exchange, 200, value);
    }

    private void mcp(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST, OPTIONS");
                writeStatus(exchange, 204, "");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeStatus(exchange, 405, "");
                return;
            }
            if (!authorized(exchange)) {
                writeJson(exchange, 401, rpcError(null, -32001, "Unauthorized"));
                return;
            }
            if (!safeOrigin(exchange)) {
                writeJson(exchange, 403, rpcError(null, -32002, "Origin is not allowed"));
                return;
            }

            byte[] raw = exchange.getRequestBody().readNBytes(config.maxBodyBytes() + 1);
            if (raw.length > config.maxBodyBytes()) {
                writeJson(exchange, 413, rpcError(null, -32003, "Request body too large"));
                return;
            }
            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            } catch (RuntimeException error) {
                writeJson(exchange, 400, rpcError(null, -32700, "Parse error"));
                return;
            }
            if (!parsed.isJsonObject()) {
                writeJson(exchange, 400, rpcError(null, -32600, "Invalid Request"));
                return;
            }

            JsonObject request = parsed.getAsJsonObject();
            JsonElement id = request.get("id");
            String method = request.has("method") ? request.get("method").getAsString() : "";
            if (method.startsWith("notifications/")) {
                writeStatus(exchange, 202, "");
                return;
            }

            JsonObject response = dispatch(request, id, method);
            exchange.getResponseHeaders().set("MCP-Protocol-Version", negotiatedProtocol(exchange, request));
            writeJson(exchange, 200, response);
        } catch (Throwable error) {
            writeJson(exchange, 500, rpcError(null, -32603,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
    }

    private JsonObject dispatch(JsonObject request, JsonElement id, String method) {
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params") : new JsonObject();
        return switch (method) {
            case "initialize" -> rpcResult(id, initialize(params));
            case "ping" -> rpcResult(id, new JsonObject());
            case "tools/list" -> rpcResult(id, listTools());
            case "tools/call" -> callTool(id, params);
            default -> rpcError(id, -32601, "Method not found: " + method);
        };
    }

    private JsonObject initialize(JsonObject params) {
        JsonObject result = new JsonObject();
        String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "";
        result.addProperty("protocolVersion", SUPPORTED_PROTOCOLS.contains(requested) ? requested : PROTOCOL_VERSION);

        JsonObject capabilities = new JsonObject();
        JsonObject toolCapabilities = new JsonObject();
        toolCapabilities.addProperty("listChanged", false);
        capabilities.add("tools", toolCapabilities);
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "AutoFTBQ-MCP");
        serverInfo.addProperty("version", AutoFTBQMcpMod.VERSION);
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions",
                "Read ftbq.get_context/get_book before edits and pass expected_revision to every immediate write. "
                        + "Use minecraft.capabilities before making claims about complete modpack data coverage.");
        return result;
    }

    private JsonObject listTools() {
        JsonArray array = new JsonArray();
        McpToolCatalog.all().forEach(tool -> array.add(tool.toJson()));
        JsonObject result = new JsonObject();
        result.add("tools", array);
        return result;
    }

    private JsonObject callTool(JsonElement id, JsonObject params) {
        if (!params.has("name")) return rpcError(id, -32602, "tools/call requires params.name");
        String name = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        try {
            JsonObject value = tools.invoke(name, arguments);
            boolean failed = value.has("status") && Set.of(
                    "invalid_argument", "unknown_tool", "unavailable", "permission_denied",
                    "unsupported_version", "failed", "conflict", "too_large"
            ).contains(value.get("status").getAsString());
            return rpcResult(id, toolResult(value, failed));
        } catch (IllegalArgumentException error) {
            JsonObject value = new JsonObject();
            value.addProperty("status", "invalid_argument");
            value.addProperty("message", error.getMessage());
            return rpcResult(id, toolResult(value, true));
        }
    }

    private static JsonObject toolResult(JsonObject value, boolean failed) {
        JsonObject toolResult = new JsonObject();
        JsonArray content = new JsonArray();
        JsonObject text = new JsonObject();
        text.addProperty("type", "text");
        text.addProperty("text", GSON.toJson(value));
        content.add(text);
        toolResult.add("content", content);
        toolResult.add("structuredContent", value);
        toolResult.addProperty("isError", failed);
        return toolResult;
    }

    private boolean authorized(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        return authorization != null && authorization.equals("Bearer " + token);
    }

    private boolean safeOrigin(HttpExchange exchange) {
        if (config.allowRemote()) return true;
        if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) return false;
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank() || "null".equals(origin)) return true;
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (host == null) return false;
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception error) {
            return false;
        }
    }

    private static String negotiatedProtocol(HttpExchange exchange, JsonObject request) {
        String header = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        if (header != null && SUPPORTED_PROTOCOLS.contains(header)) return header;
        if (request.has("params") && request.get("params").isJsonObject()) {
            JsonObject params = request.getAsJsonObject("params");
            if (params.has("protocolVersion")) {
                String version = params.get("protocolVersion").getAsString();
                if (SUPPORTED_PROTOCOLS.contains(version)) return version;
            }
        }
        return PROTOCOL_VERSION;
    }

    private static JsonObject rpcResult(JsonElement id, JsonObject result) {
        JsonObject response = base(id);
        response.add("result", result);
        return response;
    }

    private static JsonObject rpcError(JsonElement id, int code, String message) {
        JsonObject response = base(id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return response;
    }

    private static JsonObject base(JsonElement id) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? com.google.gson.JsonNull.INSTANCE : id.deepCopy());
        return response;
    }

    private static void writeJson(HttpExchange exchange, int status, JsonObject value) throws IOException {
        byte[] bytes = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void writeStatus(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
