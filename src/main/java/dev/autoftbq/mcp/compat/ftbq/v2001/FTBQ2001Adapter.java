package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.compat.ftbq.FtbqClientAdapter;

public enum FTBQ2001Adapter implements FtbqClientAdapter {
    INSTANCE;

    @Override public String generation() { return "2001"; }

    @Override public JsonObject capabilities() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("generation", generation());
        JsonArray features = new JsonArray();
        for (String feature : new String[]{
                "quest_read", "selection_context", "runtime_task_types", "runtime_reward_types",
                "runtime_type_defaults", "registry_evidence", "recipes", "server_resources",
                "revision_guard", "atomic_batches", "rollback", "undo", "idempotent_proposals"
        }) features.add(feature);
        result.add("features", features);
        return result;
    }

    @Override public JsonObject context() { return FTBQ2001ReadService.context(); }
    @Override public JsonObject book() { return FTBQ2001ReadService.book(); }
    @Override public JsonObject chapter(String id) { return FTBQ2001ReadService.chapter(id); }
    @Override public JsonObject quest(String id) { return FTBQ2001ReadService.quest(id); }
    @Override public JsonObject object(String id) { return FTBQ2001ReadService.object(id); }
    @Override public JsonObject query(String name, JsonObject arguments) { return FTBQ2001QueryExecutor.execute(name, arguments); }
    @Override public JsonObject listTypes(String kind) { return FTBQ2001TypeCatalog.list(kind); }
    @Override public JsonObject typeSchema(String kind, String typeId) { return FTBQ2001TypeCatalog.schema(kind, typeId); }
}
