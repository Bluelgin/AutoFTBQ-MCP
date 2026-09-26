package dev.autoftbq.mcp.forge.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.autoftbq.mcp.compat.ftbq.v2001.FTBQ2001ServerSecurity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public record GameDataRequest(String nonce, String arguments) {
    static void encode(GameDataRequest value, FriendlyByteBuf buffer) {
        buffer.writeUtf(value.nonce, 100);
        buffer.writeUtf(value.arguments, 8192);
    }

    static GameDataRequest decode(FriendlyByteBuf buffer) {
        return new GameDataRequest(buffer.readUtf(100), buffer.readUtf(8192));
    }

    static void handle(GameDataRequest value, Supplier<NetworkEvent.Context> supplied) {
        NetworkEvent.Context context = supplied.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;

            JsonObject result;
            try {
                if (!FTBQ2001ServerSecurity.snapshot(player).canEdit()) {
                    result = base("minecraft_server_runtime", "authoring_permission_required");
                    result.addProperty("status", "permission_denied");
                } else {
                    JsonObject args = JsonParser.parseString(value.arguments).getAsJsonObject();
                    String requestKind = string(args, "request_kind");
                    result = switch (requestKind) {
                        case "registry_page" -> registryPage(player.getServer(), args);
                        case "validate_registry_ids" -> validateIds(player.getServer(), args);
                        default -> resourceQuery(player.getServer(), args);
                    };
                }
            } catch (Exception error) {
                result = base("minecraft_server_runtime", "partial");
                result.addProperty("status", "error");
                result.addProperty("message", "The requested server-side game data could not be read.");
            }

            ForgeMcpNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new GameDataResponse(value.nonce, result.toString())
            );
        });
        context.setPacketHandled(true);
    }

    private static JsonObject registryPage(net.minecraft.server.MinecraftServer server, JsonObject args) {
        String registry = string(args, "registry").toLowerCase(Locale.ROOT);
        if (!"structure".equals(registry)) {
            JsonObject unsupported = base("minecraft_server_registry", "partial");
            unsupported.addProperty("status", "unsupported");
            unsupported.addProperty("registry", registry);
            return unsupported;
        }

        var values = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ResourceLocation> all = values.keySet().stream().sorted().toList();
        String query = string(args, "query").toLowerCase(Locale.ROOT);
        String namespace = string(args, "namespace");
        List<ResourceLocation> filtered = all.stream()
                .filter(id -> (namespace.isBlank() || id.getNamespace().equals(namespace))
                        && id.toString().toLowerCase(Locale.ROOT).contains(query))
                .toList();

        int offset = Math.max(0, intValue(args, "offset", 0));
        int limit = Math.max(1, Math.min(100, intValue(args, "limit", 50)));
        String version = Integer.toHexString(all.hashCode());

        JsonObject result = base("minecraft_server_registry", "complete_for_selected_registry_snapshot");
        result.addProperty("registry", registry);
        result.addProperty("data_version", version);
        if (args.has("data_version") && !version.equals(string(args, "data_version"))) {
            result.addProperty("status", "stale_version");
            return result;
        }

        JsonArray entries = new JsonArray();
        for (int i = offset; i < Math.min(filtered.size(), offset + limit); i++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", filtered.get(i).toString());
            entry.addProperty("registered", true);
            entries.add(entry);
        }
        result.add("entries", entries);
        result.addProperty("total", filtered.size());
        result.addProperty("next_offset", Math.min(filtered.size(), offset + entries.size()));
        result.addProperty("has_more", offset + entries.size() < filtered.size());
        result.addProperty("status", entries.isEmpty() ? "not_found" : "ok");
        return result;
    }

    private static JsonObject validateIds(net.minecraft.server.MinecraftServer server, JsonObject args) {
        String registry = string(args, "registry").toLowerCase(Locale.ROOT);
        JsonObject result = base("minecraft_server_registry", "exact_id_validation");
        result.addProperty("registry", registry);

        if (!"structure".equals(registry) || !args.has("ids") || !args.get("ids").isJsonArray()) {
            result.addProperty("status", "unsupported");
            return result;
        }

        JsonArray ids = args.getAsJsonArray("ids");
        if (ids.isEmpty() || ids.size() > 64) {
            result.addProperty("status", "invalid_argument");
            result.addProperty("message", "ids must contain 1 to 64 entries");
            return result;
        }

        var structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        JsonArray values = new JsonArray();
        for (var element : ids) {
            if (!element.isJsonPrimitive()) continue;
            String raw = element.getAsString().trim();
            ResourceLocation id = ResourceLocation.tryParse(raw);
            JsonObject item = new JsonObject();
            item.addProperty("id", raw);
            item.addProperty("exists", id != null && structures.containsKey(id));
            values.add(item);
        }
        result.add("results", values);
        result.addProperty("validated_count", values.size());
        result.addProperty("status", "ok");
        return result;
    }

    private static JsonObject resourceQuery(net.minecraft.server.MinecraftServer server, JsonObject args) throws Exception {
        JsonObject result = base("server_data_pack_resource", "resource_only_runtime_script_overrides_unknown");
        String dataset = string(args, "dataset");
        if (!Set.of("loot_tables", "advancements").contains(dataset)) {
            result.addProperty("status", "unsupported");
            return result;
        }

        if (!args.has("id")) {
            var entries = server.getResourceManager().listResources(
                    dataset, id -> id.getPath().endsWith(".json")
            ).keySet().stream().sorted().toList();
            String needle = string(args, "query").toLowerCase(Locale.ROOT);
            String namespace = string(args, "namespace");
            var filtered = entries.stream().filter(id ->
                    id.toString().toLowerCase(Locale.ROOT).contains(needle)
                            && (namespace.isBlank() || id.getNamespace().equals(namespace))
            ).toList();

            int offset = Math.max(0, intValue(args, "offset", 0));
            int limit = Math.max(1, Math.min(100, intValue(args, "limit", 50)));
            String version = Integer.toHexString(entries.hashCode());
            result.addProperty("data_version", version);
            if (args.has("data_version") && !version.equals(string(args, "data_version"))) {
                result.addProperty("status", "stale_version");
                return result;
            }

            JsonArray ids = new JsonArray();
            for (int i = offset; i < Math.min(filtered.size(), offset + limit); i++) {
                ResourceLocation id = filtered.get(i);
                String resourcePath = id.getPath();
                ids.add(id.getNamespace() + ":"
                        + resourcePath.substring(dataset.length() + 1, resourcePath.length() - 5));
            }
            result.add("ids", ids);
            result.addProperty("total", filtered.size());
            result.addProperty("next_offset", Math.min(filtered.size(), offset + ids.size()));
            result.addProperty("has_more", offset + ids.size() < filtered.size());
            result.addProperty("status", ids.isEmpty() ? "not_found" : "ok");
            return result;
        }

        String rawId = string(args, "id");
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null || id.getPath().contains("..")) {
            result.addProperty("status", "invalid_argument");
            return result;
        }

        var resource = server.getResourceManager().getResource(
                new ResourceLocation(id.getNamespace(), dataset + "/" + id.getPath() + ".json")
        );
        result.addProperty("id", id.toString());
        result.addProperty("dataset", dataset);
        if (resource.isEmpty()) {
            result.addProperty("status", "not_found");
            return result;
        }

        try (var input = resource.get().open()) {
            byte[] bytes = input.readNBytes(64_001);
            if (bytes.length > 64_000) {
                result.addProperty("status", "too_large");
                return result;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            result.add("data", JsonParser.parseString(json));
            result.addProperty("data_version", Integer.toHexString(json.hashCode()));
            result.addProperty("status", "ok");
            return result;
        }
    }

    private static JsonObject base(String source, String coverage) {
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", 1);
        result.addProperty("source", source);
        result.addProperty("coverage", coverage);
        return result;
    }

    private static String string(JsonObject args, String key) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString() : "";
    }

    private static int intValue(JsonObject args, String key, int fallback) {
        return args.has(key) ? args.get(key).getAsInt() : fallback;
    }
}
