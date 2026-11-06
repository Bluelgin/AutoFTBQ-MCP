package dev.autoftbq.mcp.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Gson compiler: an agent plans quests, while deterministic code validates,
 * lays them out and converts the plan into a single server-authoritative batch.
 * This class never reads Minecraft state or performs mutations.
 */
public final class CreatorBlueprintCompiler {
    public static final int MAX_QUESTS = 48;
    private static final int MAX_OBJECTS_PER_QUEST = 8;
    private static final int MAX_DEPENDENCIES = 12;
    private static final String CHAPTER_TEMP = "creator.chapter";

    private CreatorBlueprintCompiler() {}

    public record Compiled(JsonObject preview, JsonArray operations, List<String> itemIds) {}

    private record Node(String key, String title, JsonObject input, List<String> dependencies) {}

    public static Compiled compile(JsonObject blueprint) {
        if (blueprint == null) throw invalid("blueprint is required");
        only(blueprint, "chapter", "quests");
        JsonObject chapter = obj(blueprint, "chapter");
        only(chapter, "title", "subtitle", "icon", "group_id");
        String chapterTitle = text(chapter, "title", true, 256);
        JsonArray sourceQuests = array(blueprint, "quests", true);
        if (sourceQuests.size() < 1 || sourceQuests.size() > MAX_QUESTS) {
            throw invalid("quests must contain 1-" + MAX_QUESTS + " entries");
        }

        Map<String, Node> nodes = new LinkedHashMap<>();
        for (JsonElement element : sourceQuests) {
            if (!element.isJsonObject()) throw invalid("each quest must be an object");
            JsonObject q = element.getAsJsonObject();
            only(q, "key", "title", "subtitle", "description", "icon", "depends_on", "tasks", "rewards");
            String key = text(q, "key", true, 40);
            if (!key.matches("[a-zA-Z][a-zA-Z0-9_-]*")) {
                throw invalid("quest key must match [a-zA-Z][a-zA-Z0-9_-]*: " + key);
            }
            if (nodes.containsKey(key)) throw invalid("duplicate quest key: " + key);
            String title = text(q, "title", true, 256);
            if (q.has("subtitle")) text(q, "subtitle", false, 1000);
            if (q.has("icon")) text(q, "icon", false, 256);
            validateDescription(q);
            JsonArray depends = array(q, "depends_on", false);
            if (depends.size() > MAX_DEPENDENCIES) throw invalid(key + ": too many dependencies");
            Set<String> unique = new LinkedHashSet<>();
            for (JsonElement dep : depends) {
                if (!dep.isJsonPrimitive() || !dep.getAsJsonPrimitive().isString()) {
                    throw invalid(key + ": depends_on must contain keys");
                }
                String value = dep.getAsString();
                if (!unique.add(value)) throw invalid(key + ": duplicate dependency: " + value);
                if (value.equals(key)) throw invalid(key + ": self-dependency");
            }
            nodes.put(key, new Node(key, title, q, List.copyOf(unique)));
        }
        for (Node node : nodes.values()) {
            for (String dep : node.dependencies()) {
                if (!nodes.containsKey(dep)) throw invalid(node.key() + ": unknown dependency: " + dep);
            }
        }
        Map<String, Integer> depths = new HashMap<>();
        for (String key : nodes.keySet()) depth(key, nodes, depths, new HashSet<>());

        Map<Integer, List<Node>> levels = new LinkedHashMap<>();
        for (Node node : nodes.values()) {
            levels.computeIfAbsent(depths.get(node.key()), unused -> new ArrayList<>()).add(node);
        }

        JsonArray operations = new JsonArray();
        JsonObject chapterOp = operation("create_chapter");
        chapterOp.addProperty("temp_id", CHAPTER_TEMP);
        chapterOp.addProperty("title", chapterTitle);
        copy(chapter, chapterOp, "subtitle");
        copy(chapter, chapterOp, "icon");
        copy(chapter, chapterOp, "group_id");
        operations.add(chapterOp);

        JsonArray layout = new JsonArray();
        int taskCount = 0, rewardCount = 0, defaultCheckmarks = 0, edgeCount = 0;
        Set<String> itemIds = new LinkedHashSet<>();

        // Make all quests first, then their objects, then all edges. This ensures
        // references work even when a prerequisite is defined later in the plan.
        for (Node node : nodes.values()) {
            List<Node> peers = levels.get(depths.get(node.key()));
            int row = peers.indexOf(node);
            double x = depths.get(node.key()) * 5.0;
            double y = (row - (peers.size() - 1) / 2.0) * 4.0;
            JsonObject qOp = operation("create_quest");
            qOp.addProperty("temp_id", temporary(node.key()));
            qOp.addProperty("chapter_id", CHAPTER_TEMP);
            qOp.addProperty("title", node.title());
            qOp.addProperty("x", x);
            qOp.addProperty("y", y);
            copy(node.input(), qOp, "subtitle");
            copy(node.input(), qOp, "description");
            copy(node.input(), qOp, "icon");
            operations.add(qOp);

            JsonObject coordinate = new JsonObject();
            coordinate.addProperty("key", node.key());
            coordinate.addProperty("title", node.title());
            coordinate.addProperty("depth", depths.get(node.key()));
            coordinate.addProperty("x", x);
            coordinate.addProperty("y", y);
            layout.add(coordinate);
        }

        for (Node node : nodes.values()) {
            JsonArray tasks = array(node.input(), "tasks", false);
            JsonArray rewards = array(node.input(), "rewards", false);
            if (tasks.size() > MAX_OBJECTS_PER_QUEST || rewards.size() > MAX_OBJECTS_PER_QUEST) {
                throw invalid(node.key() + ": at most " + MAX_OBJECTS_PER_QUEST + " tasks and rewards each");
            }
            if (tasks.isEmpty()) {
                JsonObject task = operation("add_checkmark_task");
                task.addProperty("quest_id", temporary(node.key()));
                operations.add(task);
                taskCount++;
                defaultCheckmarks++;
            }
            for (JsonElement item : tasks) {
                operations.add(questObject(item, node.key(), false, itemIds));
                taskCount++;
            }
            for (JsonElement item : rewards) {
                operations.add(questObject(item, node.key(), true, itemIds));
                rewardCount++;
            }
        }
        for (Node node : nodes.values()) {
            for (String dep : node.dependencies()) {
                JsonObject edge = operation("add_dependency");
                edge.addProperty("quest_id", temporary(node.key()));
                edge.addProperty("dependency_id", temporary(dep));
                operations.add(edge);
                edgeCount++;
            }
        }
        // Forge's proposal packet has a 262,144-character JSON ceiling and
        // the FTBQ executor permits at most 1000 operations. Reject oversized
        // wishes here instead of displaying an impossible-to-commit preview.
        if (operations.size() > 1000 || operations.toString().length() > 200_000) {
            throw invalid("compiled blueprint exceeds the safe single-transaction limit; split it into smaller chapters");
        }
        JsonObject preview = new JsonObject();
        preview.addProperty("status", "ok");
        preview.addProperty("chapter_title", chapterTitle);
        preview.addProperty("quest_count", nodes.size());
        preview.addProperty("task_count", taskCount);
        preview.addProperty("reward_count", rewardCount);
        preview.addProperty("dependency_count", edgeCount);
        preview.addProperty("operation_count", operations.size());
        preview.addProperty("preview_hash", hash(operations.toString()));
        preview.addProperty("requires_confirmation", true);
        preview.add("layout", layout);
        JsonArray warnings = new JsonArray();
        if (defaultCheckmarks > 0) {
            warnings.add(defaultCheckmarks + " quests without tasks were given manual checkmark tasks; review gameplay goals.");
        }
        if (!itemIds.isEmpty()) {
            warnings.add("Item IDs are syntactically checked here; runtime registry checks occur on apply. Obtainability and recipe progression are NOT proven.");
        }
        if (rewardCount == 0) warnings.add("No rewards defined; confirm pacing and motivation.");
        warnings.add("Preview is structural only. It does not establish survival obtainability, custom recipe paths, visual quality or game compatibility.");
        preview.add("warnings", warnings);
        return new Compiled(preview, operations, List.copyOf(itemIds));
    }

    private static JsonObject questObject(JsonElement input, String questKey, boolean reward, Set<String> ids) {
        if (!input.isJsonObject()) throw invalid(questKey + ": task/reward must be an object");
        JsonObject raw = input.getAsJsonObject();
        only(raw, "kind", "item_id", "count", "amount");
        String kind = text(raw, "kind", true, 24);
        String operationKind = switch (kind) {
            case "item" -> reward ? "add_item_reward" : "add_item_task";
            case "checkmark" -> reward ? null : "add_checkmark_task";
            case "xp" -> reward ? "add_xp_reward" : "add_xp_task";
            case "xp_levels" -> reward ? "add_xp_levels_reward" : null;
            default -> null;
        };
        if (operationKind == null) throw invalid(questKey + ": unsupported " + (reward ? "reward" : "task") + " kind: " + kind);
        JsonObject op = operation(operationKind);
        op.addProperty("quest_id", temporary(questKey));
        switch (kind) {
            case "item" -> {
                only(raw, "kind", "item_id", "count");
                String id = text(raw, "item_id", true, 256);
                if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                    throw invalid(questKey + ": item_id must be a fully qualified resource id: " + id);
                }
                op.addProperty("item_id", id);
                op.addProperty("count", count(raw, "count", 1, 4096, 1));
                ids.add(id);
            }
            case "checkmark" -> only(raw, "kind");
            case "xp", "xp_levels" -> {
                only(raw, "kind", "amount");
                op.addProperty("amount", count(raw, "amount", 1, 100000, -1));
            }
            default -> throw invalid("unsupported object kind");
        }
        return op;
    }

    private static void validateDescription(JsonObject input) {
        if (!input.has("description")) return;
        JsonElement value = input.get("description");
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            if (value.getAsString().length() > 4000) throw invalid("description too long");
            return;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() > 24) {
            throw invalid("description must be text or up to 24 text lines");
        }
        for (JsonElement line : value.getAsJsonArray()) {
            if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString()
                    || line.getAsString().length() > 4000) {
                throw invalid("invalid description line");
            }
        }
    }

    private static int depth(String key, Map<String, Node> nodes, Map<String, Integer> depths, Set<String> visiting) {
        if (depths.containsKey(key)) return depths.get(key);
        if (!visiting.add(key)) throw invalid("dependency cycle includes: " + key);
        int max = 0;
        for (String dep : nodes.get(key).dependencies()) {
            max = Math.max(max, depth(dep, nodes, depths, visiting) + 1);
        }
        visiting.remove(key);
        depths.put(key, max);
        return max;
    }

    private static JsonObject operation(String kind) {
        JsonObject op = new JsonObject();
        op.addProperty("kind", kind);
        return op;
    }

    private static String temporary(String key) { return "creator.quest." + key; }

    private static void copy(JsonObject from, JsonObject to, String key) {
        if (from.has(key)) to.add(key, from.get(key).deepCopy());
    }

    private static JsonArray array(JsonObject from, String field, boolean required) {
        if (!from.has(field)) {
            if (required) throw invalid(field + " is required");
            return new JsonArray();
        }
        if (!from.get(field).isJsonArray()) throw invalid(field + " must be an array");
        return from.getAsJsonArray(field);
    }

    private static JsonObject obj(JsonObject from, String field) {
        if (!from.has(field) || !from.get(field).isJsonObject()) throw invalid(field + " must be an object");
        return from.getAsJsonObject(field);
    }

    private static String text(JsonObject from, String field, boolean required, int maxLen) {
        if (!from.has(field)) {
            if (required) throw invalid(field + " is required");
            return "";
        }
        JsonElement value = from.get(field);
        if (!value.isJsonPrimitive()) throw invalid(field + " must be text");
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (!primitive.isString()) throw invalid(field + " must be text");
        String result = primitive.getAsString().trim();
        if ((required && result.isEmpty()) || result.length() > maxLen) {
            throw invalid(field + " must be 1-" + maxLen + " characters");
        }
        return result;
    }

    private static int count(JsonObject from, String field, int min, int max, int defaultValue) {
        if (!from.has(field)) {
            if (defaultValue >= 0) return defaultValue;
            throw invalid(field + " is required");
        }
        JsonElement element = from.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw invalid(field + " must be an integer");
        }
        double numeric = element.getAsDouble();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric) || numeric < min || numeric > max) {
            throw invalid(field + " must be an integer between " + min + " and " + max);
        }
        return (int) numeric;
    }

    private static void only(JsonObject from, String... names) {
        Set<String> accepted = Set.of(names);
        for (String key : from.keySet()) {
            if (!accepted.contains(key)) throw invalid("unknown blueprint field: " + key);
        }
    }

    private static String hash(String canonical) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) hex.append(String.format("%02x", value & 255));
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
