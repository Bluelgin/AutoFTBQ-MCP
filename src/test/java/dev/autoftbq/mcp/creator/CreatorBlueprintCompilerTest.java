package dev.autoftbq.mcp.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreatorBlueprintCompilerTest {
    private static JsonObject blueprint(String quests) {
        return JsonParser.parseString("{\"chapter\":{\"title\":\"The Beginning\"},\"quests\":" + quests + "}").getAsJsonObject();
    }

    @Test
    void oneWishCompilesToAtomicOperationsWithDeterministicLayout() {
        JsonObject request = blueprint("""
            [
              {"key":"intro","title":"Meet the mod"},
              {"key":"wood","title":"Gather wood","depends_on":["intro"],
               "tasks":[{"kind":"item","item_id":"minecraft:oak_log","count":8}],
               "rewards":[{"kind":"xp_levels","amount":1}]},
              {"key":"stone","title":"Gather stone","depends_on":["intro"],
               "tasks":[{"kind":"item","item_id":"minecraft:cobblestone"}]},
              {"key":"milestone","title":"Build a base","depends_on":["wood","stone"],
               "tasks":[{"kind":"checkmark"}]}
            ]
            """);
        var compiled = CreatorBlueprintCompiler.compile(request);
        JsonObject summary = compiled.preview();
        assertEquals("ok", summary.get("status").getAsString());
        assertEquals(4, summary.get("quest_count").getAsInt());
        assertEquals(4, summary.get("task_count").getAsInt());
        assertEquals(3, summary.get("dependency_count").getAsInt());
        assertEquals(1, summary.get("reward_count").getAsInt());
        assertEquals(13, summary.get("operation_count").getAsInt());
        assertEquals(64, summary.get("preview_hash").getAsString().length());
        assertEquals("create_chapter", compiled.operations().get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("creator.chapter", compiled.operations().get(1).getAsJsonObject().get("chapter_id").getAsString());
        assertEquals("add_dependency", compiled.operations().get(12).getAsJsonObject().get("kind").getAsString());
        assertEquals(10.0, summary.getAsJsonArray("layout").get(3).getAsJsonObject().get("x").getAsDouble());
        assertEquals(2, compiled.itemIds().size());
        assertTrue(summary.getAsJsonArray("warnings").toString().contains("checkmark"));
        assertEquals(summary.get("preview_hash").getAsString(),
                CreatorBlueprintCompiler.compile(request.deepCopy()).preview().get("preview_hash").getAsString());
    }

    @Test
    void independentQuestRowSpacingIsStableAndCentered() {
        var result = CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A"},{"key":"b","title":"B"},{"key":"c","title":"C"}]
            """)).preview();
        JsonArray layout = result.getAsJsonArray("layout");
        assertEquals(-4.0, layout.get(0).getAsJsonObject().get("y").getAsDouble());
        assertEquals(0.0, layout.get(1).getAsJsonObject().get("y").getAsDouble());
        assertEquals(4.0, layout.get(2).getAsJsonObject().get("y").getAsDouble());
    }

    @Test
    void cyclesMissingDependenciesAndDuplicateKeysFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","depends_on":["b"]},{"key":"b","title":"B","depends_on":["a"]}]
            """)));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","depends_on":["missing"]}]
            """)));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A"},{"key":"a","title":"Again"}]
            """)));
    }

    @Test
    void unsupportedRewardsAndInventedFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","rewards":[{"kind":"command","command":"op player"}]}]
            """)));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","tasks":[{"kind":"item","item_id":"unqualified"}]}]
            """)));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","tasks":[{"kind":"item","item_id":"minecraft:oak_log","count":0}]}]
            """)));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","unknown_property":123}]
            """)));
    }

    @Test
    void alteredBlueprintProducesDifferentPreviewHash() {
        var a = CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","tasks":[{"kind":"item","item_id":"minecraft:oak_log"}]}]
            """));
        var b = CreatorBlueprintCompiler.compile(blueprint("""
            [{"key":"a","title":"A","tasks":[{"kind":"item","item_id":"minecraft:oak_log","count":2}]}]
            """));
        assertNotEquals(a.preview().get("preview_hash").getAsString(),
                b.preview().get("preview_hash").getAsString());
    }

    @Test
    void compilerRejectsBlueprintsTooLargeForServerOperationLimit() {
        JsonObject source = blueprint("[]");
        for (int i = 0; i < CreatorBlueprintCompiler.MAX_QUESTS; i++) {
            JsonObject quest = new JsonObject();
            quest.addProperty("key", "q" + i);
            quest.addProperty("title", "Quest " + i);
            JsonArray tasks = new JsonArray();
            JsonArray rewards = new JsonArray();
            for (int j = 0; j < 8; j++) {
                JsonObject task = new JsonObject();
                task.addProperty("kind", "item");
                task.addProperty("item_id", "minecraft:oak_log");
                tasks.add(task);
                JsonObject reward = new JsonObject();
                reward.addProperty("kind", "xp");
                reward.addProperty("amount", 1);
                rewards.add(reward);
            }
            quest.add("tasks", tasks);
            quest.add("rewards", rewards);
            JsonArray deps = new JsonArray();
            for (int j = 0; j < Math.min(i, 12); j++) deps.add("q" + j);
            quest.add("depends_on", deps);
            source.getAsJsonArray("quests").add(quest);
        }
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(source));
    }

    @Test
    void blueprintHasStrictSizeAndWorkBounds() {
        JsonObject input = blueprint("[{\"key\":\"a\",\"title\":\"A\"}]");
        for (int i = 0; i < CreatorBlueprintCompiler.MAX_QUESTS; i++) {
            JsonObject quest = new JsonObject();
            quest.addProperty("key", "key" + i);
            quest.addProperty("title", "Quest " + i);
            input.getAsJsonArray("quests").add(quest);
        }
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(input));
    }
}
