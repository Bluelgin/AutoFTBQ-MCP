package dev.autoftbq.mcp.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.compat.ftbq.FtbqAdapterProvider;
import dev.autoftbq.mcp.compat.ftbq.FtbqClientAdapter;
import dev.autoftbq.mcp.platform.GamePlatform;
import net.minecraft.SharedConstants;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CompatibilityManager {
    private static final List<FtbqAdapterProvider> FTBQ_ADAPTERS = new CopyOnWriteArrayList<>();

    private CompatibilityManager() {}

    public static void registerFtbqAdapter(FtbqAdapterProvider provider) {
        if (provider == null) throw new IllegalArgumentException("provider");
        FTBQ_ADAPTERS.removeIf(existing -> existing.generation().equals(provider.generation()));
        FTBQ_ADAPTERS.add(provider);
    }

    public static String ftbqVersion() {
        return GamePlatform.environment().modVersion("ftbquests");
    }

    public static FtbqClientAdapter currentFtbqAdapter() {
        String version = ftbqVersion();
        return FTBQ_ADAPTERS.stream()
                .filter(provider -> provider.supports(version))
                .findFirst()
                .map(FtbqAdapterProvider::adapter)
                .orElseGet(() -> new UnsupportedFtbqAdapter(version));
    }

    public static JsonObject descriptor() {
        FtbqClientAdapter adapter = currentFtbqAdapter();
        JsonObject value = new JsonObject();
        value.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        value.addProperty("loader", GamePlatform.environment().loaderId());
        value.addProperty("ftb_quests", ftbqVersion());
        value.addProperty("ftbq_generation", adapter.generation());
        value.addProperty("adapter_status",
                adapter instanceof UnsupportedFtbqAdapter ? "unsupported" : "supported");

        JsonArray registered = new JsonArray();
        FTBQ_ADAPTERS.forEach(provider -> registered.add(provider.generation()));
        value.add("registered_ftbq_generations", registered);
        return value;
    }

    private record UnsupportedFtbqAdapter(String version) implements FtbqClientAdapter {
        @Override public String generation() { return "unsupported:" + version; }

        private JsonObject unsupported() {
            JsonObject value = new JsonObject();
            value.addProperty("status", "unsupported_version");
            value.addProperty("ftb_quests_version", version);
            value.addProperty("message",
                    "No FTB Quests adapter is registered for this API generation. The runtime will not guess a nearby generation.");
            return value;
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
