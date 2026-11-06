package dev.autoftbq.mcp.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.Set;

/**
 * Optional compact, item-first input syntax. Normalizes to the existing strict
 * Creator blueprint; no FTBQ serialization or game access happens here.
 */
public final class CreatorCompactBlueprint {
    private CreatorCompactBlueprint() {}

    public static JsonObject expand(JsonObject input) {
        if (input == null || !input.has("chapter") || !input.get("chapter").isJsonPrimitive()
                || !input.get("chapter").getAsJsonPrimitive().isString()) {
            return input; // Legacy full-blueprint path: unchanged.
        }
        only(input, "chapter", "subtitle", "icon", "group_id", "quests");
        JsonObject chapter = new JsonObject();
        chapter.add("title", input.get("chapter").deepCopy());
        for (String field : new String[]{"subtitle", "icon", "group_id"}) copy(input, chapter, field);

        if (!input.has("quests") || !input.get("quests").isJsonArray()) {
            throw invalid("compact quests must be an array");
        }
        JsonArray quests = new JsonArray();
        for (JsonElement element : input.getAsJsonArray("quests")) {
            if (!element.isJsonObject()) throw invalid("compact quest must be an object");
            JsonObject source = element.getAsJsonObject();
            only(source, "id", "title", "desc", "icon", "get", "n", "after", "give", "give_n");
            String key = string(source, "id", true);
            String item = string(source, "get", false);
            String rewardItem = string(source, "give", false);
            if (source.has("n") && item == null) throw invalid("n requires get");
            if (source.has("give_n") && rewardItem == null) throw invalid("give_n requires give");
            JsonObject quest = new JsonObject();
            quest.addProperty("key", key);
            if (source.has("title")) {
                copy(source, quest, "title");
            } else {
                quest.addProperty("title", item != null
                        ? "Obtain " + humanize(item.substring(item.indexOf(':') + 1))
                        : humanize(key));
            }
            if (source.has("desc")) quest.add("description", source.get("desc").deepCopy());
            if (source.has("icon")) copy(source, quest, "icon");
            else if (item != null) quest.addProperty("icon", item);

            if (source.has("after")) {
                JsonElement after = source.get("after");
                if (after.isJsonPrimitive() && after.getAsJsonPrimitive().isString()) {
                    JsonArray refs = new JsonArray();
                    refs.add(after.getAsString());
                    quest.add("depends_on", refs);
                } else if (after.isJsonArray()) {
                    quest.add("depends_on", after.deepCopy());
                } else throw invalid("after must be a quest key or an array of keys");
            }

            if (item != null) {
                JsonObject task = new JsonObject();
                task.addProperty("kind", "item");
                task.addProperty("item_id", item);
                if (source.has("n")) copy(source, task, "n", "count");
                JsonArray tasks = new JsonArray();
                tasks.add(task);
                quest.add("tasks", tasks);
            }
            if (rewardItem != null) {
                JsonObject reward = new JsonObject();
                reward.addProperty("kind", "item");
                reward.addProperty("item_id", rewardItem);
                if (source.has("give_n")) copy(source, reward, "give_n", "count");
                JsonArray rewards = new JsonArray();
                rewards.add(reward);
                quest.add("rewards", rewards);
            }
            quests.add(quest);
        }
        JsonObject expanded = new JsonObject();
        expanded.add("chapter", chapter);
        expanded.add("quests", quests);
        return expanded;
    }

    private static String string(JsonObject value, String field, boolean required) {
        if (!value.has(field)) {
            if (required) throw invalid("missing compact field: " + field);
            return null;
        }
        JsonElement element = value.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw invalid(field + " must be text");
        }
        return element.getAsString();
    }

    private static String humanize(String id) {
        String normalized = id.replace('_', ' ').replace('-', ' ').replace('/', ' ').trim();
        if (normalized.isEmpty()) return "Untitled Quest";
        String[] words = normalized.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (title.length() > 0) title.append(' ');
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            title.append(word.substring(1));
        }
        return title.toString();
    }

    private static void copy(JsonObject from, JsonObject to, String key) {
        if (from.has(key)) to.add(key, from.get(key).deepCopy());
    }

    private static void copy(JsonObject from, JsonObject to, String fromKey, String toKey) {
        if (from.has(fromKey)) to.add(toKey, from.get(fromKey).deepCopy());
    }

    private static void only(JsonObject source, String... names) {
        Set<String> allowed = Set.of(names);
        for (String name : source.keySet()) {
            if (!allowed.contains(name)) throw invalid("unknown compact field: " + name);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
