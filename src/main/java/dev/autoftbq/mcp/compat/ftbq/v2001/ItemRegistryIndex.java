package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Client-local, bounded item lookup. Never sends the full registry to an LLM.
 * This only proves registered IDs and translated display names, not obtainability.
 */
final class ItemRegistryIndex {
    private static String indexVersion;
    private static List<Entry> index = List.of();

    private record Entry(String id, String namespace, String name, String searchableName) {}

    private ItemRegistryIndex() {}

    static JsonObject search(JsonObject request, String dataVersion) {
        String query = field(request, "query").trim().toLowerCase(Locale.ROOT);
        String namespace = field(request, "namespace").trim().toLowerCase(Locale.ROOT);
        JsonObject result = new JsonObject();
        result.addProperty("data_version", dataVersion);
        result.addProperty("source", "minecraft_client_registry");
        result.addProperty("coverage", "registered_items_only");
        if (query.length() > 80 || namespace.length() > 80
                || (query.isEmpty() && namespace.isEmpty())) {
            result.addProperty("status", "invalid_argument");
            result.addProperty("message", "Supply a query or namespace (up to 80 chars); full-registry dump is intentionally disabled.");
            return result;
        }
        int offset = bounded(request, "offset", 0, 10000, 0);
        int limit = bounded(request, "limit", 1, 30, 20);
        List<Entry> entries = entries(dataVersion);
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (!namespace.isEmpty() && !namespace.equals(entry.namespace())) continue;
            if (!query.isEmpty() && !entry.id().contains(query) && !entry.searchableName().contains(query)) continue;
            matches.add(entry);
        }
        matches.sort(Comparator.comparingInt((Entry value) -> rank(value, query)).thenComparing(Entry::id));
        JsonArray found = new JsonArray();
        for (int i = Math.min(offset, matches.size()); i < Math.min(matches.size(), offset + limit); i++) {
            Entry entry = matches.get(i);
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.id());
            item.addProperty("display_name", entry.name());
            found.add(item);
        }
        result.add("matches", found);
        result.addProperty("total_matches", matches.size());
        result.addProperty("has_more", offset + found.size() < matches.size());
        result.addProperty("next_offset", offset + found.size());
        result.addProperty("status", found.isEmpty() ? "not_found" : "ok");
        result.addProperty("caution", "Registered does not mean obtainable; inspect recipes or other gameplay evidence.");
        return result;
    }

    private static int rank(Entry value, String query) {
        if (value.id().equals(query)) return 0;
        if (value.id().startsWith(query) || value.id().substring(value.id().indexOf(':') + 1).startsWith(query)) return 1;
        if (value.searchableName().startsWith(query)) return 2;
        return 3;
    }

    private static synchronized List<Entry> entries(String version) {
        String key = version + ":" + System.identityHashCode(Language.getInstance());
        if (key.equals(indexVersion)) return index;
        List<Entry> items = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            String name = BuiltInRegistries.ITEM.get(id).getDescription().getString();
            items.add(new Entry(id.toString(), id.getNamespace(), name, name.toLowerCase(Locale.ROOT)));
        }
        index = List.copyOf(items);
        indexVersion = key;
        return index;
    }

    private static String field(JsonObject request, String key) {
        return request.has(key) && !request.get(key).isJsonNull() ? request.get(key).getAsString() : "";
    }

    private static int bounded(JsonObject request, String name, int minimum, int maximum, int fallback) {
        if (!request.has(name)) return fallback;
        int value = request.get(name).getAsInt();
        return Math.max(minimum, Math.min(maximum, value));
    }
}
