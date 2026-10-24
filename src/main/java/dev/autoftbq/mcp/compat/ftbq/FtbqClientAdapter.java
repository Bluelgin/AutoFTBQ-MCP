package dev.autoftbq.mcp.compat.ftbq;

import com.google.gson.JsonObject;

public interface FtbqClientAdapter {
    String generation();
    JsonObject capabilities();
    JsonObject context();
    JsonObject book();
    JsonObject chapter(String id);
    JsonObject quest(String id);
    JsonObject object(String id);
    JsonObject query(String name, JsonObject arguments);
    JsonObject listTypes(String kind);
    JsonObject typeSchema(String kind, String typeId);
}
