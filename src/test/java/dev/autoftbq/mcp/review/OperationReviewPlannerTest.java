package dev.autoftbq.mcp.review;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationReviewPlannerTest {
    private static JsonArray operations(String source) {
        return JsonParser.parseString(source).getAsJsonArray();
    }

    private static JsonObject target(String type, String id) {
        JsonObject value = new JsonObject();
        value.addProperty("status", "ok");
        value.addProperty("id", id);
        value.addProperty("title", "Existing Title");
        value.addProperty("x", 2);
        value.addProperty("data_snbt", "{addon:\"must_never_appear_in_preview\"}");
        return value;
    }

    @Test
    void previewsExistingQuestUpdateAndDestructiveEditWithoutLeakingSnbt() {
        JsonArray request = operations("""
            [
              {"kind":"update_quest","quest_id":"1234","changes":{"title":"Better title","x":8}},
              {"kind":"remove_quest_object","object_id":"8888"}
            ]""");
        var result = OperationReviewPlanner.prepare(request, "revA", "revA",
                OperationReviewPlannerTest::target);
        assertEquals("ok", result.preview().get("status").getAsString());
        assertEquals(2, result.preview().get("operation_count").getAsInt());
        assertEquals(1, result.preview().get("destructive_count").getAsInt());
        assertEquals(0, result.preview().get("effect_unverified_count").getAsInt());
        assertTrue(result.preview().getAsJsonArray("changes").get(1)
                .getAsJsonObject().get("destructive").getAsBoolean());
        assertFalse(result.preview().toString().contains("must_never_appear"));
        assertFalse(result.preview().toString().contains("Better title"));
        assertEquals(64, result.preview().get("preview_hash").getAsString().length());
        request.get(0).getAsJsonObject().getAsJsonObject("changes").addProperty("title", "Mutated");
        assertTrue(result.operations().toString().contains("Better title"));
    }

    @Test
    void rejectsStaleRevisionMissingTargetsAndUnreviewableOperations() {
        JsonArray update = operations("""
            [{"kind":"update_quest","quest_id":"1234","changes":{"title":"New"}}]""");
        assertThrows(IllegalStateException.class,
                () -> OperationReviewPlanner.prepare(update, "old", "new",
                        OperationReviewPlannerTest::target));
        assertThrows(IllegalArgumentException.class,
                () -> OperationReviewPlanner.prepare(update, "rev", "rev",
                        (type, id) -> {
                            JsonObject missing = new JsonObject();
                            missing.addProperty("status", "not_found");
                            return missing;
                        }));
        assertThrows(IllegalArgumentException.class,
                () -> OperationReviewPlanner.prepare(operations("""
                   [{"kind":"update_book_raw","data_snbt":"{}"}]"""),
                   "rev", "rev", OperationReviewPlannerTest::target));
        assertThrows(IllegalArgumentException.class,
                () -> OperationReviewPlanner.prepare(operations("[]"), "rev", "rev",
                        OperationReviewPlannerTest::target));
    }

    @Test
    void creationAndLocalTemporaryReferencesHaveNoFakeBeforeState() {
        var plan = OperationReviewPlanner.prepare(operations("""
            [
              {"kind":"create_chapter","temp_id":"new.chapter","title":"Beginning"},
              {"kind":"create_quest","temp_id":"new.quest","chapter_id":"new.chapter","title":"Wood","x":0,"y":0},
              {"kind":"add_item_task","quest_id":"new.quest","item_id":"minecraft:oak_log","count":8}
            ]
            """), "rev", "rev", (type, id) -> {
            fail("Must not query uncommitted temporary target " + id);
            return null;
        });
        assertEquals(3, plan.preview().get("operation_count").getAsInt());
        assertTrue(plan.preview().getAsJsonArray("changes").get(2)
                .getAsJsonObject().get("target_from_this_plan").getAsBoolean());
    }

    @Test
    void flagsUnverifiedThirdPartyDataAndMakesHashRevisionBound() {
        JsonArray request = operations("""
            [{"kind":"patch_quest_object","object_id":"8888","changes_snbt":"{custom:1}"}]""");
        var a = OperationReviewPlanner.prepare(request, "r1", "r1",
                OperationReviewPlannerTest::target);
        var b = OperationReviewPlanner.prepare(request, "r2", "r2",
                OperationReviewPlannerTest::target);
        assertEquals(1, a.preview().get("effect_unverified_count").getAsInt());
        assertTrue(a.preview().getAsJsonArray("changes").get(0)
                .getAsJsonObject().get("effect_unverified").getAsBoolean());
        assertNotEquals(a.preview().get("preview_hash"), b.preview().get("preview_hash"));
    }
}
