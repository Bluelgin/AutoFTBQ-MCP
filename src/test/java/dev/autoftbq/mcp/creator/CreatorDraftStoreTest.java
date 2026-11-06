package dev.autoftbq.mcp.creator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreatorDraftStoreTest {
    private static CreatorBlueprintCompiler.Compiled plan() {
        JsonObject source = JsonParser.parseString("""
            {"chapter":{"title":"Basics"},"quests":[{"key":"wood","title":"Gather Wood",
              "tasks":[{"kind":"item","item_id":"minecraft:oak_log","count":4}]}]}
            """).getAsJsonObject();
        return CreatorBlueprintCompiler.compile(source);
    }

    @Test
    void draftIsIsolatedFromMutableCallerAndResolver() {
        CreatorDraftStore drafts = new CreatorDraftStore();
        var source = plan();
        var saved = drafts.remember(source);
        assertEquals(36, saved.id().length());
        assertTrue(saved.expiresAtMillis() > System.currentTimeMillis());
        String expectedHash = source.preview().get("preview_hash").getAsString();
        source.operations().get(0).getAsJsonObject().addProperty("title", "Tampered");
        var first = drafts.resolve(saved.id());
        assertNotNull(first);
        assertEquals(expectedHash, first.preview().get("preview_hash").getAsString());
        assertEquals("Basics", first.operations().get(0).getAsJsonObject().get("title").getAsString());
        first.operations().get(0).getAsJsonObject().addProperty("title", "Tampered Again");
        assertEquals("Basics", drafts.resolve(saved.id()).operations().get(0)
                .getAsJsonObject().get("title").getAsString());
    }

    @Test
    void draftEvictsOldestAndCannotResolveUnknownId() {
        CreatorDraftStore drafts = new CreatorDraftStore();
        String first = drafts.remember(plan()).id();
        for (int i = 0; i < 16; i++) drafts.remember(plan());
        assertNull(drafts.resolve(first));
        assertNull(drafts.resolve("missing"));
    }
}
