package dev.autoftbq.mcp.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ChapterMapRendererTest {
    private static JsonObject node(String id, String title, double x, double y, String... parents) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("title", title);
        o.addProperty("x", x);
        o.addProperty("y", y);
        JsonArray dependencies = new JsonArray();
        for (String p : parents) dependencies.add(p);
        o.add("dependencies", dependencies);
        return o;
    }

    private static JsonObject chapter(JsonObject... quests) {
        JsonObject c = new JsonObject();
        c.addProperty("chapter_id", "DEADBEEF");
        c.addProperty("chapter_title", "Industrial Progression");
        c.addProperty("book_revision", "rev1");
        JsonArray list = new JsonArray();
        for (JsonObject q : quests) list.add(q);
        c.add("quests", list);
        return c;
    }

    @Test
    void previewIsCompletePngWithStableIdLegend() throws Exception {
        JsonObject result = ChapterMapRenderer.render(chapter(
                node("A", "First step", 0, 0),
                node("B", "Machinery", 3, -2, "A"),
                node("C", "Energy", 3, 2, "A"),
                node("D", "Capstone", 7, 0, "B", "C")
        ), 960);
        assertEquals("ok", result.get("status").getAsString());
        assertFalse(result.get("native_ftbq_render").getAsBoolean());
        assertTrue(result.get("whole_chapter").getAsBoolean());
        assertEquals(4, result.getAsJsonArray("quest_legend").size());
        assertEquals("D", result.getAsJsonArray("quest_legend").get(3).getAsJsonObject().get("quest_id").getAsString());
        assertEquals(4, result.get("internal_dependency_count").getAsInt());
        byte[] data = Base64.getDecoder().decode(result.get("_image_base64").getAsString());
        assertNotNull(ImageIO.read(new ByteArrayInputStream(data)));
        assertEquals(960, ImageIO.read(new ByteArrayInputStream(data)).getWidth());
    }

    @Test
    void emptyChapterStillProducesOverview() {
        JsonObject result = ChapterMapRenderer.render(chapter(), 640);
        assertEquals(0, result.getAsJsonArray("quest_legend").size());
        assertTrue(result.get("whole_chapter").getAsBoolean());
    }

    @Test
    void malformedCoordinatesAndDuplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ChapterMapRenderer.render(chapter(node("A", "Bad", Double.NaN, 0)), 900));
        assertThrows(IllegalArgumentException.class, () ->
                ChapterMapRenderer.render(chapter(node("A", "One", 0, 0), node("A", "Two", 1, 1)), 900));
        assertThrows(IllegalArgumentException.class, () -> ChapterMapRenderer.render(chapter(), 500));
    }
}
