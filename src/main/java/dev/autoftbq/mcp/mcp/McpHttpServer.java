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
    public static final String PROTOCOL_VERSION = "2026-07-28";
    private static final String LEGACY_DEFAULT = "2025-11-25";
    private static final Set<String> LEGACY_PROTOCOLS = Set.of(
            "2025-11-25", "2025-06-18", "2025-03-26"
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
        value.addProperty("latestProtocolVersion", PROTOCOL_VERSION);
        JsonArray versions = new JsonArray();
        versions.add(PROTOCOL_VERSION);
        versions.add("2025-11-25");
        versions.add("2025-06-18");
        versions.add("2025-03-26");
        value.add("supportedProtocolVersions", versions);
        writeJson(exchange, 200, value);
    }

    private void mcp(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST, OPTIONS");
                writeStatus(exchange, 204, "");
                return;
            }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                // We do not currently expose a long-lived server->client SSE channel.
                // Streamable HTTP permits servers that do not offer this optional path
                // to reject it with Method Not Allowed.
                writeStatus(exchange, 405, "");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeStatus(exchange, 405, "");
                return;
            }
            if (!authorized(exchange)) {
                writeJson(exchange, 401, rpcError(null, -32001, "Unauthorized", false));
                return;
            }
            if (!safeOrigin(exchange)) {
                writeJson(exchange, 403, rpcError(null, -32002, "Origin is not allowed", false));
                return;
            }

            byte[] raw = exchange.getRequestBody().readNBytes(config.maxBodyBytes() + 1);
            if (raw.length > config.maxBodyBytes()) {
                writeJson(exchange, 413, rpcError(null, -32003, "Request body too large", false));
                return;
            }

            JsonElement parsed;
            try {
                parsed = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            } catch (RuntimeException error) {
                writeJson(exchange, 400, rpcError(null, -32700, "Parse error", false));
                return;
            }
            if (!parsed.isJsonObject()) {
                writeJson(exchange, 400, rpcError(null, -32600, "Invalid Request", false));
                return;
            }

            JsonObject request = parsed.getAsJsonObject();
            JsonElement id = request.get("id");
            String method = request.has("method") ? request.get("method").getAsString() : "";
            boolean modern = isModern(exchange, method);

            if (modern) {
                String headerError = validateModernHeaders(exchange, request, method);
                if (headerError != null) {
                    writeJson(exchange, 400, rpcError(id, -32020, headerError, true));
                    return;
                }
            }

            if (method.startsWith("notifications/")) {
                writeStatus(exchange, 202, "");
                return;
            }

            JsonObject response = dispatch(request, id, method, modern);
            exchange.getResponseHeaders().set("MCP-Protocol-Version",
                    modern ? PROTOCOL_VERSION : negotiatedLegacyProtocol(exchange, request));
            writeJson(exchange, 200, response);
        } catch (Throwable error) {
            writeJson(exchange, 500, rpcError(null, -32603,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), false));
        }
    }

    private JsonObject dispatch(JsonObject request, JsonElement id, String method, boolean modern) {
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params") : new JsonObject();

        if (modern) {
            return switch (method) {
                case "server/discover" -> rpcResult(id, discover(), true);
                case "tools/list" -> rpcResult(id, listTools(true), true);
                case "tools/call" -> callTool(id, params, true);
                default -> rpcError(id, -32601, "Method not found in 2026-07-28: " + method, true);
            };
        }

        return switch (method) {
            case "initialize" -> rpcResult(id, initialize(params), false);
            case "ping" -> rpcResult(id, new JsonObject(), false);
            case "tools/list" -> rpcResult(id, listTools(false), false);
            case "tools/call" -> callTool(id, params, false);
            default -> rpcError(id, -32601, "Method not found: " + method, false);
        };
    }

    private JsonObject discover() {
        JsonObject result = new JsonObject();
        JsonArray versions = new JsonArray();
        versions.add(PROTOCOL_VERSION);
        versions.add("2025-11-25");
        versions.add("2025-06-18");
        versions.add("2025-03-26");
        result.add("supportedVersions", versions);
        result.add("capabilities", capabilities());
        result.addProperty("instructions", instructions());
        result.addProperty("ttlMs", 60_000);
        result.addProperty("cacheScope", "private");
        stampServerInfo(result);
        return result;
    }

    private JsonObject initialize(JsonObject params) {
        JsonObject result = new JsonObject();
        String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "";
        result.addProperty("protocolVersion", LEGACY_PROTOCOLS.contains(requested) ? requested : LEGACY_DEFAULT);
        result.add("capabilities", capabilities());

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "AutoFTBQ-MCP");
        serverInfo.addProperty("version", AutoFTBQMcpMod.VERSION);
        result.add("serverInfo", serverInfo);
        result.addProperty("instructions", instructions());
        return result;
    }

    private static JsonObject capabilities() {
        JsonObject capabilities = new JsonObject();
        JsonObject toolCapabilities = new JsonObject();
        toolCapabilities.addProperty("listChanged", false);
        capabilities.add("tools", toolCapabilities);
        return capabilities;
    }

    private static String instructions() {
        return "Read ftbq.get_context or ftbq.get_book before edits and pass expected_revision "
                + "to every immediate write. Use minecraft.capabilities before making claims about "
                + "complete modpack data coverage. Prefer semantic ftbq.* tools; use ftbq.apply_operations "
                + "only when a semantic tool does not cover the object.";
    }

    private JsonObject listTools(boolean modern) {
        JsonArray array = new JsonArray();
        McpToolCatalog.all().forEach(tool -> array.add(tool.toJson()));
        JsonObject result = new JsonObject();
        result.add("tools", array);
        if (modern) {
            result.addProperty("ttlMs", 30_000);
            result.addProperty("cacheScope", "private");
        }
        return result;
    }

    private JsonObject callTool(JsonElement id, JsonObject params, boolean modern) {
        if (!params.has("name")) return rpcError(id, -32602, "tools/call requires params.name", modern);
        String name = params.get("name").getAsString();
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments") : new JsonObject();
        try {
            JsonObject value = tools.invoke(name, arguments);
            boolean failed = value.has("status") && Set.of(
                    "invalid_argument", "unknown_tool", "unavailable", "permission_denied",
                    "unsupported_version", "failed", "conflict", "too_large"
            ).contains(value.get("status").getAsString());
            return rpcResult(id, toolResult(value, failed), modern);
        } catch (IllegalArgumentException error) {
            JsonObject value = new JsonObject();
            value.addProperty("status", "invalid_argument");
            value.addProperty("message", error.getMessage());
            return rpcResult(id, toolResult(value, true), modern);
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
            return host != null && InetAddress.getByName(host).isLoopbackAddress();
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean isModern(HttpExchange exchange, String method) {
        String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        return PROTOCOL_VERSION.equals(version) || "server/discover".equals(method);
    }

    private static String validateModernHeaders(HttpExchange exchange, JsonObject request, String method) {
        String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        if (!PROTOCOL_VERSION.equals(version)) {
            return "MCP-Protocol-Version must be " + PROTOCOL_VERSION;
        }
        String routedMethod = exchange.getRequestHeaders().getFirst("Mcp-Method");
        if (routedMethod == null || !routedMethod.equals(method)) {
            return "Mcp-Method header does not match JSON-RPC method";
        }
        if ("tools/call".equals(method)) {
            JsonObject params = request.has("params") && request.get("params").isJsonObject()
                    ? request.getAsJsonObject("params") : null;
            String name = params != null && params.has("name") ? params.get("name").getAsString() : "";
            String routedName = exchange.getRequestHeaders().getFirst("Mcp-Name");
            if (routedName == null || !routedName.equals(name)) {
                return "Mcp-Name header does not match params.name";
            }
        }
        return null;
    }

    private static String negotiatedLegacyProtocol(HttpExchange exchange, JsonObject request) {
        String header = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        if (header != null && LEGACY_PROTOCOLS.contains(header)) return header;
        if (request.has("params") && request.get("params").isJsonObject()) {
            JsonObject params = request.getAsJsonObject("params");
            if (params.has("protocolVersion")) {
                String version = params.get("protocolVersion").getAsString();
                if (LEGACY_PROTOCOLS.contains(version)) return version;
            }
        }
        return LEGACY_DEFAULT;
    }

    private static JsonObject rpcResult(JsonElement id, JsonObject result, boolean modern) {
        if (modern) stampServerInfo(result);
        JsonObject response = base(id);
        response.add("result", result);
        return response;
    }

    private static JsonObject rpcError(JsonElement id, int code, String message, boolean modern) {
        JsonObject response = base(id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        if (modern) stampServerInfo(error);
        response.add("error", error);
        return response;
    }

    private static void stampServerInfo(JsonObject value) {
        JsonObject meta = value.has("_meta") && value.get("_meta").isJsonObject()
                ? value.getAsJsonObject("_meta") : new JsonObject();
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "AutoFTBQ-MCP");
        serverInfo.addProperty("version", AutoFTBQMcpMod.VERSION);
        meta.add("io.modelcontextprotocol/serverInfo", serverInfo);
        value.add("_meta", meta);
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
