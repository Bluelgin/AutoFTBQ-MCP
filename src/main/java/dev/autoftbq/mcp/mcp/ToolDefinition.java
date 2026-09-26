package dev.autoftbq.mcp.mcp;

import com.google.gson.JsonObject;

public record ToolDefinition(String name, String description, JsonObject inputSchema) {
    public JsonObject toJson() {
        JsonObject value = new JsonObject();
        value.addProperty("name", name);
        value.addProperty("description", description);
        value.add("inputSchema", inputSchema);
        return value;
    }
}
