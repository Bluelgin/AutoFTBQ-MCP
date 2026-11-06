package dev.autoftbq.mcp.review;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReviewDraftStoreTest {
    private static OperationReviewPlanner.Plan plan() {
        return OperationReviewPlanner.prepare(
                JsonParser.parseString("""
                    [{"kind":"update_quest","quest_id":"1234","changes":{"title":"Edited"}}]
                    """).getAsJsonArray(), "rev", "rev",
                (type, id) -> {
                    var obj = new com.google.gson.JsonObject();
                    obj.addProperty("status", "ok");
                    obj.addProperty("id", id);
                    obj.addProperty("title", "Before");
                    return obj;
                });
    }

    @Test
    void savedDraftsCannotBeMutatedThroughOriginalOrReadback() {
        ReviewDraftStore store = new ReviewDraftStore();
        var source = plan();
        var ticket = store.store(source, "session-A");
        assertEquals(ReviewDraftStore.TTL_SECONDS, ticket.ttlSeconds());
        source.operations().get(0).getAsJsonObject().addProperty("kind", "delete_quest");
        var copy = store.resolve(ticket.id(), "session-A");
        assertEquals("update_quest", copy.operations().get(0).getAsJsonObject().get("kind").getAsString());
        copy.operations().get(0).getAsJsonObject().addProperty("kind", "delete_quest");
        assertEquals("update_quest", store.resolve(ticket.id(), "session-A").operations()
                .get(0).getAsJsonObject().get("kind").getAsString());
        assertNull(store.resolve("not-present", "session-A"));
        assertNull(store.resolve(ticket.id(), "session-B")); // A different world/server cannot reuse the draft.
    }

    @Test
    void storeIsBoundedToSixteenDrafts() {
        ReviewDraftStore store = new ReviewDraftStore();
        String first = store.store(plan(), "session-A").id();
        for (int i = 0; i < 16; i++) store.store(plan(), "session-A");
        assertNull(store.resolve(first, "session-A"));
    }
}
