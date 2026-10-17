package dev.autoftbq.mcp.design;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-chapter structural preview, not an FTB Quests framebuffer screenshot.
 * Independent of Minecraft rendering and the user's open GUI/zoom.
 */
public final class ChapterMapRenderer {
    private static final int MAX_DIMENSION = 2048;
    private static final int MAX_IMAGE_BYTES = 2_500_000;
    private static final Color BACKGROUND = new Color(20, 24, 33);
    private static final Color CARD = new Color(39, 48, 64);
    private static final Color FONT = new Color(235, 239, 248);
    private static final Color SECONDARY = new Color(164, 176, 193);
    private static final Color LINE = new Color(111, 133, 169, 180);

    private ChapterMapRenderer() {}

    public static JsonObject render(JsonObject chapter, int maxDimension) {
        if (maxDimension < 640 || maxDimension > MAX_DIMENSION) {
            throw new IllegalArgumentException("max_dimension must be in 640..2048");
        }
        if (chapter == null || !chapter.has("quests") || !chapter.get("quests").isJsonArray()) {
            throw new IllegalArgumentException("chapter snapshot with a quests array required");
        }
        JsonArray source = chapter.getAsJsonArray("quests");
        if (source.size() > 512) throw new IllegalArgumentException("at most 512 quests supported");
        Map<String, Point> points = new LinkedHashMap<>();
        List<Point> ordered = new ArrayList<>();
        int ix = 0;
        double lowX = Double.POSITIVE_INFINITY, lowY = Double.POSITIVE_INFINITY;
        double highX = Double.NEGATIVE_INFINITY, highY = Double.NEGATIVE_INFINITY;
        for (JsonElement item : source) {
            JsonObject obj = item.getAsJsonObject();
            String id = str(obj, "id");
            if (id.isBlank() || points.containsKey(id)) throw new IllegalArgumentException("quest IDs must be unique and nonempty");
            double x = obj.get("x").getAsDouble(), y = obj.get("y").getAsDouble();
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("quest coordinates must be finite");
            JsonArray deps = obj.has("dependencies") && obj.get("dependencies").isJsonArray()
                    ? obj.getAsJsonArray("dependencies") : new JsonArray();
            Point point = new Point(id, str(obj, "title"), x, y, ++ix, deps);
            ordered.add(point);
            points.put(id, point);
            lowX = Math.min(lowX, x); highX = Math.max(highX, x);
            lowY = Math.min(lowY, y); highY = Math.max(highY, y);
        }

        // Preserve canvas proportions without allowing pathological coordinates
        // to create enormous images. All nodes remain in bounds.
        double rangeX = source.isEmpty() ? 1 : Math.max(1.0, highX - lowX);
        double rangeY = source.isEmpty() ? 1 : Math.max(1.0, highY - lowY);
        double aspect = rangeX / rangeY;
        int width = maxDimension;
        int height = Math.max(480, (int) Math.round((maxDimension - 90) / Math.max(0.70, Math.min(3.0, aspect)) + 90));
        height = Math.min(MAX_DIMENSION, height);
        double contentWidth = width - 200.0;
        double contentHeight = height - 180.0;
        double scaleX = contentWidth / rangeX;
        double scaleY = contentHeight / rangeY;
        for (Point point : ordered) {
            point.pixelX = (int) Math.round(100 + (point.x - (source.isEmpty() ? 0 : lowX)) * scaleX);
            point.pixelY = (int) Math.round(115 + (point.y - (source.isEmpty() ? 0 : lowY)) * scaleY);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.setColor(FONT);
        g.drawString(clamp(str(chapter, "chapter_title"), 60), 30, 38);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(SECONDARY);
        g.drawString("Structural map | Entire chapter | Not a native FTBQ screenshot", 30, 63);
        g.setColor(LINE);
        g.setStroke(new BasicStroke(2.0f));
        int edges = 0;
        int outside = 0;
        for (Point child : ordered) {
            for (JsonElement item : child.dependencies) {
                if (!item.isJsonPrimitive()) continue;
                Point parent = points.get(item.getAsString());
                if (parent == null) { outside++; continue; }
                if (edges++ > 4096) break;
                double startX = parent.pixelX, startY = parent.pixelY;
                double endX = child.pixelX, endY = child.pixelY;
                Path2D line = new Path2D.Double();
                line.moveTo(startX, startY);
                double midX = startX + (endX - startX) / 2;
                line.curveTo(midX, startY, midX, endY, endX, endY);
                g.draw(line);
                double angle = Math.atan2(endY - startY, endX - midX);
                double ax = endX - 13 * Math.cos(angle), ay = endY - 13 * Math.sin(angle);
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(endX, endY);
                arrow.lineTo(ax + 5 * Math.sin(angle), ay - 5 * Math.cos(angle));
                arrow.lineTo(ax - 5 * Math.sin(angle), ay + 5 * Math.cos(angle));
                arrow.closePath();
                g.fill(arrow);
            }
        }

        // Label nodes by stable ordinal AND title. Legend in JSON ties each ordinal
        // to a real quest ID; the agent must never infer IDs from screenshot text.
        JsonArray legend = new JsonArray();
        g.setStroke(new BasicStroke(1.4f));
        for (Point node : ordered) {
            int x = node.pixelX, y = node.pixelY;
            g.setColor(CARD);
            g.fillRoundRect(x - 69, y - 24, 138, 48, 10, 10);
            g.setColor(new Color(97, 144, 199));
            g.drawRoundRect(x - 69, y - 24, 138, 48, 10, 10);
            g.setColor(FONT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.drawString("#" + node.index + "  " + clamp(node.title, 14), x - 59, y - 2);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g.setColor(SECONDARY);
            g.drawString(clamp(node.id, 19), x - 59, y + 14);
            JsonObject entry = new JsonObject();
            entry.addProperty("index", node.index);
            entry.addProperty("quest_id", node.id);
            entry.addProperty("title", node.title);
            entry.addProperty("pixel_x", x);
            entry.addProperty("pixel_y", y);
            legend.add(entry);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(SECONDARY);
        g.drawString(ordered.size() + " quests  |  " + edges + " internal links  |  " + outside +
                " external prerequisites  |  Rendered from live coordinates", 30, height - 17);
        g.dispose();

        byte[] png;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) throw new IOException("No PNG encoder found");
            png = bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Could not encode chapter overview", error);
        }
        if (png.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Overview PNG exceeds 2.5 MB; retry with lower max_dimension");
        }

        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("source", "software_quest_graph_preview");
        result.addProperty("native_ftbq_render", false);
        result.addProperty("whole_chapter", true);
        result.addProperty("width", width);
        result.addProperty("height", height);
        result.addProperty("mime_type", "image/png");
        result.addProperty("internal_dependency_count", edges);
        result.addProperty("external_dependency_count", outside);
        result.addProperty("note", "Graph preview may differ from FTBQ icons, chapter images, scaling and theme. Use actual native screenshot for visual QA.");
        if (chapter.has("book_revision")) result.add("book_revision", chapter.get("book_revision").deepCopy());
        if (chapter.has("chapter_id")) result.add("chapter_id", chapter.get("chapter_id").deepCopy());
        result.add("quest_legend", legend);
        result.addProperty("_image_base64", Base64.getEncoder().encodeToString(png));
        return result;
    }

    private static String str(JsonObject obj, String name) {
        return obj.has(name) && !obj.get(name).isJsonNull() ? obj.get(name).getAsString() : "";
    }
    private static String clamp(String value, int max) {
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "…";
    }
    private static final class Point {
        final String id, title;
        final double x, y;
        final int index;
        final JsonArray dependencies;
        int pixelX, pixelY;
        Point(String id, String title, double x, double y, int index, JsonArray dependencies) {
            this.id = id; this.title = title; this.x = x; this.y = y;
            this.index = index; this.dependencies = dependencies;
        }
    }
}
