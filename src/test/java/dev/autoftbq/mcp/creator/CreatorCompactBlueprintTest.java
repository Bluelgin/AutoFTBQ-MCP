package dev.autoftbq.mcp.creator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CreatorCompactBlueprintTest {
    private static JsonObject json(String source) {
        return JsonParser.parseString(source).getAsJsonObject();
    }

    @Test
    void compactBlueprintExpandsToExistingAtomicOperations() {
        JsonObject compact = json("""
            {"chapter":"Create Basics","quests":[
              {"id":"alloy","get":"create:andesite_alloy","n":8,"give":"minecraft:iron_ingot","give_n":2},
              {"id":"press","get":"create:mechanical_press","after":"alloy","desc":"Power your press"}
            ]}
            """);
        JsonObject expanded = CreatorCompactBlueprint.expand(compact);
        var compiled = CreatorBlueprintCompiler.compile(expanded);
        assertEquals(2, compiled.preview().get("quest_count").getAsInt());
        assertEquals(2, compiled.preview().get("task_count").getAsInt());
        assertEquals(1, compiled.preview().get("reward_count").getAsInt());
        assertEquals(1, compiled.preview().get("dependency_count").getAsInt());
        assertEquals("Obtain Andesite Alloy", expanded.getAsJsonArray("quests").get(0)
                .getAsJsonObject().get("title").getAsString());
        assertEquals("create:andesite_alloy", expanded.getAsJsonArray("quests").get(0)
                .getAsJsonObject().get("icon").getAsString());
        assertEquals("create:mechanical_press", compiled.itemIds().get(2));
        assertEquals("add_dependency", compiled.operations().get(compiled.operations().size() - 1)
                .getAsJsonObject().get("kind").getAsString());
        // Normalization never edits the submitted user blueprint.
        assertFalse(compact.getAsJsonArray("quests").get(0).getAsJsonObject().has("tasks"));
    }

    @Test
    void compactAndFullEquivalentBlueprintsCompileToIdenticalPlanHashes() {
        var compact = json("""
            {"chapter":"Basics","quests":[{"id":"wood","title":"Gather Wood",
              "get":"minecraft:oak_log","n":8},
              {"id":"bench","title":"Craft Bench","get":"minecraft:crafting_table","after":["wood"]}]}
            """);
        var full = json("""
            {"chapter":{"title":"Basics"},"quests":[
              {"key":"wood","title":"Gather Wood","icon":"minecraft:oak_log",
               "tasks":[{"kind":"item","item_id":"minecraft:oak_log","count":8}]},
              {"key":"bench","title":"Craft Bench","icon":"minecraft:crafting_table",
               "depends_on":["wood"],"tasks":[{"kind":"item","item_id":"minecraft:crafting_table"}]}
            ]}
            """);
        var shorter = CreatorBlueprintCompiler.compile(CreatorCompactBlueprint.expand(compact));
        var traditional = CreatorBlueprintCompiler.compile(full);
        // Explicit default count on the first quest matches; omitted default on the second does too.
        assertEquals(traditional.operations().toString(), shorter.operations().toString());
        assertEquals(traditional.preview().get("preview_hash").getAsString(),
                shorter.preview().get("preview_hash").getAsString());
        assertSame(full, CreatorCompactBlueprint.expand(full));
    }

    @Test
    void compactRejectsUnsupportedOrDanglingSyntaxBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> CreatorCompactBlueprint.expand(json("""
            {"chapter":"X","quests":[{"id":"a","n":8}]}""")));
        assertThrows(IllegalArgumentException.class, () -> CreatorCompactBlueprint.expand(json("""
            {"chapter":"X","quests":[{"id":"a","give_n":3}]}""")));
        assertThrows(IllegalArgumentException.class, () -> CreatorCompactBlueprint.expand(json("""
            {"chapter":"X","quests":[{"id":"a","get":"minecraft:stone","data_snbt":"{}"}]}""")));
        assertThrows(IllegalArgumentException.class, () -> CreatorCompactBlueprint.expand(json("""
            {"chapter":"X","quests":[{"id":"a","after":123}]}""")));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(
                CreatorCompactBlueprint.expand(json("""
                {"chapter":"X","quests":[{"id":"a","get":"not_a_real_resource_id"}]}"""))));
        assertThrows(IllegalArgumentException.class, () -> CreatorBlueprintCompiler.compile(
                CreatorCompactBlueprint.expand(json("""
                {"chapter":"X","quests":[{"id":"a","after":"b"},{"id":"b","after":"a"}]}"""))));
    }
}
