package dev.autoftbq.mcp.review;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, read-only review of intended edits. Not a dry run of FTBQ
 * serialization: actual validity, permissions and rollback remain server-only.
 */
public final class OperationReviewPlanner {
    private static final int MAX_OPERATIONS = 128;
    private static final int MAX_JSON_CHARS = 200_000;

    private static final Set<String> CREATE = Set.of("create_chapter", "create_quest");
    private static final Set<String> DELETE = Set.of("delete_chapter", "delete_quest",
            "remove_quest_object");
    private static final Set<String> MODIFY = Set.of(
            "update_chapter", "update_quest", "move_quest", "move_chapter_to_group",
            "reorder_chapter", "patch_quest_object", "move_quest_object");
    private static final Set<String> ADD_OBJECT = Set.of(
            "add_item_task", "add_checkmark_task", "add_xp_task", "add_item_reward",
            "add_xp_reward", "add_xp_levels_reward", "add_typed_quest_object");
    private static final Set<String> EDGE = Set.of("add_dependency", "remove_dependency");

    @FunctionalInterface
    public interface TargetReader {
        JsonObject read(String kind, String id);
    }

    public record Plan(JsonObject preview, JsonArray operations, String expectedRevision) {}

    private OperationReviewPlanner() {}

    public static Plan prepare(JsonArray operations, String expectedRevision, String actualRevision,
                               TargetReader reader) {
        if (operations == null || operations.isEmpty() || operations.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("preview supports 1-128 operations; split larger plans");
        }
        if (operations.toString().length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("preview operations exceed 200000 JSON characters");
        }
        if (expectedRevision == null || expectedRevision.isBlank()) {
            throw new IllegalArgumentException("expected_revision is required");
        }
        if (!expectedRevision.equals(actualRevision)) {
            throw new IllegalStateException("stale_revision");
        }

        JsonArray changes = new JsonArray();
        JsonArray warnings = new JsonArray();
        warnings.add("Advisory intent preview only: no FTBQ operations were executed or fully dry-run. Server performs final validation on commit.");
        Set<String> createdTemps = new HashSet<>();
        Map<String, JsonObject> cache = new HashMap<>();
        int destructive = 0;
        int unverified = 0;

        for (int i = 0; i < operations.size(); i++) {
            JsonElement element = operations.get(i);
            if (!element.isJsonObject()) throw invalid(i, "operation must be object");
            JsonObject op = element.getAsJsonObject();
            String kind = require(op, "kind", i);
            String targetType, field;
            if (kind.equals("create_chapter")) { targetType = "chapter"; field = "temp_id"; }
            else if (kind.equals("create_quest")) { targetType = "quest"; field = "temp_id"; }
            else if (kind.equals("delete_chapter") || kind.equals("update_chapter")
                    || kind.equals("move_chapter_to_group") || kind.equals("reorder_chapter")) {
                targetType = "chapter"; field = "chapter_id";
            } else if (kind.equals("patch_quest_object") || kind.equals("remove_quest_object")
                    || kind.equals("move_quest_object")) {
                targetType = "object"; field = "object_id";
            } else if (kind.equals("delete_quest") || kind.equals("update_quest")
                    || kind.equals("move_quest") || ADD_OBJECT.contains(kind) || EDGE.contains(kind)) {
                targetType = "quest"; field = "quest_id";
            } else {
                // Unsupported kinds remain available through the explicit
                // advanced apply_operations interface, not falsely previewed.
                throw invalid(i, "operation kind is not safely reviewable: " + kind);
            }
            String id = require(op, field, i);
            boolean creation = CREATE.contains(kind);
            if (creation && !createdTemps.add(id)) throw invalid(i, "duplicate temp_id: " + id);

            JsonObject before = new JsonObject();
            if (!creation && !createdTemps.contains(id)) {
                String key = targetType + ":" + id;
                JsonObject raw = cache.computeIfAbsent(key, unused -> reader.read(targetType, id));
                if (raw == null || !raw.has("status") || !"ok".equals(raw.get("status").getAsString())) {
                    throw invalid(i, "existing target not found/unavailable: " + targetType + " " + id);
                }
                before = safeSummary(raw);
            }
            if (EDGE.contains(kind)) {
                String prerequisite = require(op, "dependency_id", i);
                if (!createdTemps.contains(prerequisite)) {
                    String key = "quest:" + prerequisite;
                    JsonObject prerequisiteState = cache.computeIfAbsent(key,
                            unused -> reader.read("quest", prerequisite));
                    if (prerequisiteState == null || !prerequisiteState.has("status")
                            || !"ok".equals(prerequisiteState.get("status").getAsString())) {
                        throw invalid(i, "dependency quest not found/unavailable: " + prerequisite);
                    }
                }
            }

            JsonObject change = new JsonObject();
            change.addProperty("index", i);
            change.addProperty("kind", kind);
            change.addProperty("target_type", targetType);
            change.addProperty("target_id", id);
            change.addProperty("action", creation ? "create" : DELETE.contains(kind) ? "delete"
                    : EDGE.contains(kind) ? "dependency" : ADD_OBJECT.contains(kind) ? "append"
                    : "modify");
            if (before.size() > 0) change.add("before", before);
            if (createdTemps.contains(id) && !creation) change.addProperty("target_from_this_plan", true);

            JsonArray fields = new JsonArray();
            if (op.has("changes") && op.get("changes").isJsonObject()) {
                op.getAsJsonObject("changes").keySet().forEach(fields::add);
            } else {
                for (String key : op.keySet()) {
                    if (!Set.of("kind", "quest_id", "chapter_id", "object_id",
                            "temp_id", "data_snbt", "changes_snbt", "changes").contains(key)) {
                        fields.add(key);
                    }
                }
            }
            change.add("requested_fields", fields);

            boolean destructiveEdit = DELETE.contains(kind);
            if (destructiveEdit) {
                change.addProperty("destructive", true);
                destructive++;
            }
            boolean rawOrUnknown = kind.equals("patch_quest_object")
                    || kind.equals("add_typed_quest_object");
            if (rawOrUnknown) {
                change.addProperty("effect_unverified", true);
                unverified++;
            }
            change.addProperty("after_state_verified", false);
            changes.add(change);
        }
        if (destructive > 0) warnings.add(destructive + " destructive operations; check affected child tasks/rewards and player progress.");
        if (unverified > 0) warnings.add(unverified + " typed/SNBT edits require inspecting exact data; actual addon behavior cannot be proven here.");

        JsonObject preview = new JsonObject();
        preview.addProperty("status", "ok");
        preview.addProperty("preview_kind", "intent_not_simulation");
        preview.addProperty("book_revision", expectedRevision);
        preview.addProperty("operation_count", operations.size());
        preview.addProperty("destructive_count", destructive);
        preview.addProperty("effect_unverified_count", unverified);
        preview.addProperty("requires_confirmation", true);
        preview.addProperty("preview_hash", hash(expectedRevision + "\n" + operations.toString()));
        preview.add("changes", changes);
        preview.add("warnings", warnings);
        return new Plan(preview, operations.deepCopy(), expectedRevision);
    }

    private static JsonObject safeSummary(JsonObject raw) {
        JsonObject safe = new JsonObject();
        for (String field : new String[]{"id", "title", "object_type", "type_id",
                "chapter_id", "x", "y", "task_count", "reward_count"}) {
            if (raw.has(field)) safe.add(field, raw.get(field).deepCopy());
        }
        // Never return the target's bulky data_snbt in a review.
        return safe;
    }

    private static String require(JsonObject op, String key, int index) {
        if (!op.has(key) || !op.get(key).isJsonPrimitive()
                || !op.get(key).getAsJsonPrimitive().isString()
                || op.get(key).getAsString().isBlank()) {
            throw invalid(index, key + " must be a nonblank string");
        }
        String result = op.get(key).getAsString();
        if (result.length() > 256) throw invalid(index, key + " too long");
        return result;
    }

    private static IllegalArgumentException invalid(int index, String message) {
        return new IllegalArgumentException("operation " + index + ": " + message);
    }

    private static String hash(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte part : digest) value.append(String.format("%02x", part & 255));
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
