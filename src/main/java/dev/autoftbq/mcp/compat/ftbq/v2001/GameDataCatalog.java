package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/** Evidence-bearing, paged client data. Unknown is never equivalent to unavailable. */
public final class GameDataCatalog {
    private static final AtomicLong GENERATION = new AtomicLong();

    private GameDataCatalog() {}

    public static void invalidate() {
        GENERATION.incrementAndGet();
    }

    private static String text(JsonObject a, String key) {
        return a != null && a.has(key) && !a.get(key).isJsonNull() ? a.get(key).getAsString() : "";
    }

    public static JsonObject query(JsonObject args) {
        try {
            return queryInternal(args == null ? new JsonObject() : args);
        } catch (RuntimeException failure) {
            JsonObject result = new JsonObject();
            result.addProperty("schema_version", 1);
            result.addProperty("source", "minecraft_client_runtime");
            result.addProperty("status", "error");
            result.addProperty("message", "Live game-data query failed; retry after the client finishes reloading.");
            return result;
        }
    }

    private static JsonObject queryInternal(JsonObject args) {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject result = new JsonObject();
        String version = Integer.toHexString(System.identityHashCode(minecraft.level))
                + ":" + GENERATION.get();

        result.addProperty("schema_version", 1);
        result.addProperty("data_version", version);
        result.addProperty("source", "minecraft_client_runtime");
        result.addProperty("coverage", "partial");

        String expected = text(args, "data_version");
        if (!expected.isBlank() && !expected.equals(version)) {
            result.addProperty("status", "stale_version");
            result.addProperty("message", "Game data reloaded; restart pagination from the first page.");
            return result;
        }

        String kind = text(args, "kind");
        if ("capabilities".equals(kind)) {
            JsonArray supported = new JsonArray();
            for (String entry : new String[]{
                    "registry_page:item", "registry_page:block", "registry_page:entity",
                    "registry_page:fluid", "registry_page:mob_effect", "registry_page:stat",
                    "registry_page:recipe_type", "registry_page:biome", "registry_page:dimension",
                    "registry_page:advancement",
                    "item_evidence", "recipes",
                    "server_resource:loot_tables", "server_resource:advancements"
            }) supported.add(entry);
            result.add("supported", supported);

            JsonArray unknown = new JsonArray();
            for (String entry : new String[]{
                    "survival_obtainability", "runtime_loot_overrides", "villager_trades",
                    "scripted_drops", "custom_machine_adapters", "research_gates",
                    "boss_summoning_rules", "modpack_disable_rules"
            }) unknown.add(entry);
            result.add("not_covered", unknown);
            result.addProperty("status", "ok");
            return result;
        }

        if (minecraft.level == null) {
            result.addProperty("status", "unavailable");
            result.addProperty("message", "No client world is loaded.");
            return result;
        }

        if ("registry_page".equals(kind)) {
            String registryName = text(args, "registry");
            int offset = Math.max(0, args.has("offset") ? args.get("offset").getAsInt() : 0);
            int limit = Math.max(1, Math.min(100, args.has("limit") ? args.get("limit").getAsInt() : 50));
            String needle = text(args, "query").toLowerCase(Locale.ROOT);
            String namespace = text(args, "namespace");

            if ("dimension".equals(registryName) || "advancement".equals(registryName)) {
                KnownServerRegistries known = KnownServerRegistries.client;
                if (known == null) {
                    result.addProperty("status", "unavailable");
                    result.addProperty("message", "FTB Library has not received the server registry snapshot yet.");
                    return result;
                }

                java.util.List<ResourceLocation> ids = ("dimension".equals(registryName)
                        ? known.dimensions.stream()
                        : known.advancements.keySet().stream())
                        .filter(id -> (namespace.isBlank() || id.getNamespace().equals(namespace))
                                && id.toString().toLowerCase(Locale.ROOT).contains(needle))
                        .sorted()
                        .toList();

                JsonArray entries = new JsonArray();
                for (int i = offset; i < Math.min(ids.size(), offset + limit); i++) {
                    ResourceLocation id = ids.get(i);
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", id.toString());
                    entry.addProperty("registered", true);
                    if ("advancement".equals(registryName)) {
                        KnownServerRegistries.AdvancementInfo info = known.advancements.get(id);
                        if (info != null && info.name != null) {
                            entry.addProperty("display_name", info.name.getString());
                        }
                    }
                    entries.add(entry);
                }

                result.addProperty("registry", registryName);
                result.add("entries", entries);
                result.addProperty("total", ids.size());
                result.addProperty("next_offset", Math.min(ids.size(), offset + entries.size()));
                result.addProperty("has_more", offset + entries.size() < ids.size());
                result.addProperty("coverage", "ftb_library_server_registry_snapshot");
                result.addProperty("status", entries.isEmpty() ? "not_found" : "ok");
                return result;
            }

            Registry<?> registry = registry(registryName);
            if (registry == null) {
                result.addProperty("status", "unsupported");
                result.addProperty("message", "This registry is not available from the current client adapter.");
                return result;
            }

            var ids = registry.keySet().stream()
                    .filter(id -> (namespace.isBlank() || id.getNamespace().equals(namespace))
                            && id.toString().toLowerCase(Locale.ROOT).contains(needle))
                    .sorted()
                    .toList();

            JsonArray entries = new JsonArray();
            for (int i = offset; i < Math.min(ids.size(), offset + limit); i++) {
                ResourceLocation id = ids.get(i);
                JsonObject entry = new JsonObject();
                entry.addProperty("id", id.toString());
                entry.addProperty("registered", true);
                entries.add(entry);
            }

            result.addProperty("registry", registryName);
            result.add("entries", entries);
            result.addProperty("total", ids.size());
            result.addProperty("next_offset", Math.min(ids.size(), offset + entries.size()));
            result.addProperty("has_more", offset + entries.size() < ids.size());
            result.addProperty("coverage", "complete_for_selected_registry_snapshot");
            result.addProperty("status", entries.isEmpty() ? "not_found" : "ok");
            return result;
        }

        if ("item_evidence".equals(kind)) {
            ResourceLocation id = ResourceLocation.tryParse(text(args, "item_id"));
            boolean exists = id != null && BuiltInRegistries.ITEM.getOptional(id).isPresent();
            result.addProperty("registered", exists);
            result.addProperty("status", exists ? "ok" : "not_found");
            result.addProperty("availability", exists ? "unknown" : "invalid_id");
            result.addProperty("obtainability", "unknown");
            result.addProperty("development_complete", "unknown");
            if (!exists) return result;

            var item = BuiltInRegistries.ITEM.get(id);
            result.addProperty("id", id.toString());
            result.addProperty("name", item.getDescription().getString());

            JsonArray tags = new JsonArray();
            item.builtInRegistryHolder().tags().limit(256)
                    .forEach(tag -> tags.add(tag.location().toString()));
            result.add("tags", tags);
            result.addProperty("tags_truncated",
                    item.builtInRegistryHolder().tags().count() > 256);

            try {
                var model = minecraft.getItemRenderer().getItemModelShaper().getItemModel(item);
                result.addProperty("render_status", model.isCustomRenderer()
                        ? "custom_renderer_unverified"
                        : model == minecraft.getModelManager().getMissingModel()
                        ? "missing_model" : "base_model_present");
            } catch (RuntimeException error) {
                result.addProperty("render_status", "unknown");
            }

            result.addProperty("interpretation",
                    "Registration, tags and a client model do not prove survival obtainability or content completeness.");
            return result;
        }

        if ("recipes".equals(kind)) {
            JsonObject data = FTBQ2001QueryExecutor.execute("search_recipes", args);
            result.add("data", data);
            result.addProperty("status", data.has("error") ? "error" : "ok");
            return result;
        }

        result.addProperty("status", "unsupported");
        result.addProperty("message",
                "The current adapter does not cover this data source; absence must not be interpreted as non-existence.");
        return result;
    }

    private static Registry<?> registry(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        return switch (name) {
            case "item" -> BuiltInRegistries.ITEM;
            case "block" -> BuiltInRegistries.BLOCK;
            case "entity", "entity_type" -> BuiltInRegistries.ENTITY_TYPE;
            case "fluid" -> BuiltInRegistries.FLUID;
            case "mob_effect" -> BuiltInRegistries.MOB_EFFECT;
            case "stat", "custom_stat" -> BuiltInRegistries.CUSTOM_STAT;
            case "recipe_type" -> BuiltInRegistries.RECIPE_TYPE;
            case "biome" -> minecraft.level == null ? null
                    : minecraft.level.registryAccess().registry(Registries.BIOME).orElse(null);
            default -> null;
        };
    }
}
