package dev.autoftbq.mcp.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestDesignAnalyzerTest {
    private static JsonObject quest(String id, double x, double y, int tasks, int rewards, String... dependencies) {
        JsonObject q = new JsonObject();
        q.addProperty("id", id);
        q.addProperty("title", "Quest " + id);
        q.addProperty("x", x);
        q.addProperty("y", y);
        JsonArray deps = new JsonArray();
        for (String value : dependencies) deps.add(value);
        q.add("dependencies", deps);
        JsonArray taskList = new JsonArray();
        for (int i = 0; i < tasks; i++) {
            JsonObject v = new JsonObject();
            v.addProperty("type_id", "ftbquests:item");
            taskList.add(v);
        }
        q.add("tasks", taskList);
        JsonArray rewardList = new JsonArray();
        for (int i = 0; i < rewards; i++) {
            JsonObject v = new JsonObject();
            v.addProperty("type_id", "ftbquests:item");
            rewardList.add(v);
        }
        q.add("rewards", rewardList);
        return q;
    }
    private static JsonObject chapter(JsonObject... quests) {
        JsonObject json = new JsonObject();
        json.addProperty("chapter_id", "BEEF");
        JsonArray array = new JsonArray();
        for (JsonObject quest : quests) array.add(quest);
        json.add("quests", array);
        return json;
    }

    @Test
    void linearTutorialProducesThreeTeachingPhases() {
        JsonObject p = QuestDesignAnalyzer.analyze(chapter(
                quest("A", 0, 0, 1, 0),
                quest("B", 3, 0, 1, 1, "A"),
                quest("C", 6, 0, 2, 1, "B")), 2);
        assertTrue(p.get("acyclic").getAsBoolean());
        assertEquals("linear", p.getAsJsonObject("progression").get("structure").getAsString());
        assertEquals(3, p.getAsJsonArray("progression_phases").size());
        assertEquals(0, p.getAsJsonObject("layout_assessment").get("overlap_pairs").getAsInt());
        assertEquals(2, p.getAsJsonObject("objectives_and_rewards").get("reward_count").getAsInt());
    }

    @Test
    void branchedMilestoneIsIdentifiedAsTeachingGate() {
        JsonObject p = QuestDesignAnalyzer.analyze(chapter(
                quest("A", 0, 2, 1, 0),
                quest("B", 3, 0, 1, 0, "A"),
                quest("C", 3, 4, 1, 0, "A"),
                quest("D", 7, 2, 2, 1, "B", "C")), 2);
        JsonObject progression = p.getAsJsonObject("progression");
        assertEquals("branch_and_converge", progression.get("structure").getAsString());
        assertTrue(progression.get("has_explicit_final_convergence").getAsBoolean());
        assertEquals(1, progression.get("branch_points").getAsInt());
        assertEquals(1, progression.get("convergence_points").getAsInt());
        assertEquals(3, p.getAsJsonArray("progression_phases").size());
    }

    @Test
    void actualCyclesAreReportedNotSilentlyConsideredValid() {
        JsonObject p = QuestDesignAnalyzer.analyze(chapter(
                quest("A", 0, 0, 1, 0, "B"),
                quest("B", 3, 0, 1, 0, "A")), 2);
        assertFalse(p.get("acyclic").getAsBoolean());
        assertEquals(-1, p.getAsJsonObject("progression").get("longest_prerequisite_chain").getAsInt());
    }

    @Test
    void layoutFindsOverlappingNodes() {
        JsonObject p = QuestDesignAnalyzer.analyze(chapter(
                quest("A", 1, 1, 1, 0),
                quest("B", 1, 1, 1, 0)), 2);
        assertEquals(1, p.getAsJsonObject("layout_assessment").get("overlap_pairs").getAsInt());
    }

    @Test
    void designDifferenceFlagsLossOfFinalConvergence() {
        JsonObject ref = chapter(
                quest("A", 0, 1, 1, 0),
                quest("B", 3, 0, 1, 0, "A"),
                quest("C", 3, 3, 1, 0, "A"),
                quest("D", 6, 1, 1, 1, "B", "C"));
        JsonObject replacement = chapter(
                quest("W", 0, 0, 1, 0),
                quest("X", 3, 0, 1, 1, "W"),
                quest("Y", 6, 0, 1, 0, "X"));
        JsonObject diff = QuestDesignAnalyzer.compare(ref, replacement, 2);
        assertTrue(diff.getAsJsonArray("differences_to_review").toString().contains("final gate"));
    }

    @Test
    void rejectInvalidCoordinatesOrOverSizedBooks() {
        assertThrows(IllegalArgumentException.class,
                () -> QuestDesignAnalyzer.analyze(chapter(quest("A", 0, 0, 0, 0)), Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> QuestDesignAnalyzer.analyze(chapter(quest("A", Double.NaN, 0, 0, 0)), 2));
        JsonObject duplicate = chapter(quest("A", 0, 0, 0, 0), quest("A", 1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> QuestDesignAnalyzer.analyze(duplicate, 2));
    }
}
