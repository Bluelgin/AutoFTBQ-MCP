package dev.autoftbq.mcp.compat;

import com.google.gson.JsonObject;
import dev.autoftbq.mcp.compat.ftbq.FtbqClientAdapter;
import dev.autoftbq.mcp.compat.ftbq.v2001.FTBQ2001Adapter;
import net.minecraft.SharedConstants;
import net.minecraftforge.fml.ModList;

public final class CompatibilityManager {
    private CompatibilityManager() {}

    public static String ftbqVersion() {
        return ModList.get().getModContainerById("ftbquests")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("unavailable");
    }

    public static FtbqClientAdapter currentFtbqAdapter() {
        String version = ftbqVersion();
        if (version.startsWith("2001.")) return FTBQ2001Adapter.INSTANCE;
        return new UnsupportedFtbqAdapter(version);
    }

    public static JsonObject descriptor() {
        JsonObject value = new JsonObject();
        value.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        value.addProperty("loader", "forge");
        value.addProperty("ftb_quests", ftbqVersion());
        value.addProperty("ftbq_generation", currentFtbqAdapter().generation());
        value.addProperty("adapter_status", currentFtbqAdapter() instanceof UnsupportedFtbqAdapter ? "unsupported" : "supported");
        return value;
    }

    private record UnsupportedFtbqAdapter(String version) implements FtbqClientAdapter {
        @Override public String generation() { return "unsupported:" + version; }
        private JsonObject unsupported() {
            JsonObject v = new JsonObject();
            v.addProperty("status", "unsupported_version");
            v.addProperty("ftb_quests_version", version);
            v.addProperty("message", "No FTB Quests adapter is registered for this API generation.");
            return v;
        }
        @Override public JsonObject capabilities() { return unsupported(); }
        @Override public JsonObject context() { return unsupported(); }
        @Override public JsonObject book() { return unsupported(); }
        @Override public JsonObject chapter(String id) { return unsupported(); }
        @Override public JsonObject quest(String id) { return unsupported(); }
        @Override public JsonObject object(String id) { return unsupported(); }
        @Override public JsonObject query(String name, JsonObject arguments) { return unsupported(); }
        @Override public JsonObject listTypes(String kind) { return unsupported(); }
        @Override public JsonObject typeSchema(String kind, String typeId) { return unsupported(); }
    }
}
