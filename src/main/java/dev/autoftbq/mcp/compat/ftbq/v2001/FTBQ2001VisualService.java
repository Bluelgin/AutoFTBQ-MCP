package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.autoftbq.mcp.design.QuestDesignAnalyzer;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;

import java.io.IOException;
import java.util.Base64;

/**
 * FTB Quests 2001-specific bridge from the live editor to version-independent
 * design analysis. Does not change book data or infer gameplay availability.
 */
public final class FTBQ2001VisualService {
    private static final int MAX_QUESTS = 512;
    private static final int MAX_BYTES = 2_500_000;

    private FTBQ2001VisualService() {}

    public static JsonObject snapshot(JsonObject args) {
        if (!ClientQuestFile.exists()) return error("unavailable", "FTB Quests client book is not loaded.");
        String chapterId = string(args, "chapter_id");
        if (chapterId.isBlank()) return error("invalid_argument", "chapter_id is required.");
        JsonObject read;
        try {
            read = FTBQ2001ReadService.chapter(chapterId);
        } catch (RuntimeException ex) {
            return error("invalid_argument", "Invalid chapter identifier.");
        }
        if (!"ok".equals(string(read, "status"))) return read;
        Chapter chapter = ClientQuestFile.INSTANCE.getChapter(
                dev.ftb.mods.ftbquests.quest.QuestObjectBase.parseCodeString(chapterId));
        if (chapter == null) return error("not_found", "Chapter does not exist.");
        if (chapter.getQuests().size() > MAX_QUESTS) return error("too_large", "At most 512 quests can be analyzed at once.");

        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("chapter_id", chapter.getCodeString());
        result.addProperty("chapter_title", chapter.getTitle().getString());
        result.addProperty("book_revision", FTBQ2001Revision.compute(ClientQuestFile.INSTANCE));
        result.addProperty("data_origin", "live_client_ftbq_2001");
        JsonArray quests = new JsonArray();
        for (Quest quest : chapter.getQuests()) {
            JsonObject q = new JsonObject();
            q.addProperty("id", quest.getCodeString());
            q.addProperty("title", quest.getTitle().getString());
            q.addProperty("x", quest.getX());
            q.addProperty("y", quest.getY());
            JsonArray dependencies = new JsonArray();
            quest.streamDependencies().limit(256)
                    .forEach(value -> dependencies.add(value.getCodeString()));
            q.add("dependencies", dependencies);
            JsonArray tasks = new JsonArray();
            quest.getTasks().forEach(task -> {
                JsonObject v = new JsonObject();
                v.addProperty("type_id", task.getType().getTypeId().toString());
                v.addProperty("title", task.getTitle().getString());
                CompoundTag data = new CompoundTag();
                task.writeData(data);
                v.addProperty("data_snbt", bounded(data.toString(), 1024));
                tasks.add(v);
            });
            q.add("tasks", tasks);
            JsonArray rewards = new JsonArray();
            quest.getRewards().forEach(reward -> {
                JsonObject v = new JsonObject();
                v.addProperty("type_id", reward.getType().getTypeId().toString());
                v.addProperty("title", reward.getTitle().getString());
                CompoundTag data = new CompoundTag();
                reward.writeData(data);
                v.addProperty("data_snbt", bounded(data.toString(), 1024));
                rewards.add(v);
            });
            q.add("rewards", rewards);
            quests.add(q);
        }
        result.add("quests", quests);
        result.addProperty("quest_count", quests.size());
        result.addProperty("limits", "512 quests per chapter; dependency list capped at 256 per quest; SNBT excerpt capped at 1024 chars per task/reward.");
        return result;
    }

    public static JsonObject designProfile(JsonObject args) {
        JsonObject data = snapshot(args);
        if (!"ok".equals(string(data, "status"))) return data;
        return QuestDesignAnalyzer.analyze(data, spacing(args));
    }

    public static JsonObject compareDesign(JsonObject args) {
        String referenceId = string(args, "reference_chapter_id");
        String candidateId = string(args, "candidate_chapter_id");
        if (referenceId.isBlank() || candidateId.isBlank()) {
            return error("invalid_argument", "reference_chapter_id and candidate_chapter_id are required.");
        }
        JsonObject refArgs = new JsonObject(), candArgs = new JsonObject();
        refArgs.addProperty("chapter_id", referenceId);
        candArgs.addProperty("chapter_id", candidateId);
        JsonObject reference = snapshot(refArgs), candidate = snapshot(candArgs);
        if (!"ok".equals(string(reference, "status"))) return reference;
        if (!"ok".equals(string(candidate, "status"))) return candidate;
        JsonObject result = QuestDesignAnalyzer.compare(reference, candidate, spacing(args));
        result.addProperty("book_revision", FTBQ2001Revision.compute(ClientQuestFile.INSTANCE));
        return result;
    }

    public static JsonObject layoutAssessment(JsonObject args) {
        JsonObject data = designProfile(args);
        if (!"ok".equals(string(data, "status"))) return data;
        JsonObject response = new JsonObject();
        response.addProperty("status", "ok");
        response.add("chapter_id", data.get("chapter_id"));
        response.add("book_revision", data.get("book_revision"));
        response.add("geometry", data.get("geometry"));
        response.add("layout_assessment", data.get("layout_assessment"));
        response.addProperty("note", "Coordinate geometry cannot prove actual FTBQ rendering. Capture a real frame for visual judgement.");
        return response;
    }

    public static JsonObject captureView(JsonObject args) {
        if (!ClientQuestFile.exists()) return error("unavailable", "FTB Quests book is not loaded.");
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof QuestScreen)) {
            return error("unavailable", "Open the native FTB Quests quest screen before capturing; the MCP tool will not switch screens.");
        }
        int requested = args.has("max_dimension") ? args.get("max_dimension").getAsInt() : 1600;
        if (requested < 512 || requested > 2048) return error("invalid_argument", "max_dimension must be 512..2048.");
        try (NativeImage screen = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            int originalWidth = screen.getWidth(), originalHeight = screen.getHeight();
            if (originalWidth < 1 || originalHeight < 1) return error("unavailable", "Framebuffer is empty.");
            double ratio = Math.min(1.0, (double) requested / Math.max(originalWidth, originalHeight));
            int width = Math.max(1, (int) Math.round(originalWidth * ratio));
            int height = Math.max(1, (int) Math.round(originalHeight * ratio));
            byte[] png;
            if (ratio == 1.0) {
                png = screen.asByteArray();
            } else {
                try (NativeImage scaled = new NativeImage(width, height, true)) {
                    screen.resizeSubRectTo(0, 0, originalWidth, originalHeight, scaled);
                    png = scaled.asByteArray();
                }
            }
            if (png.length > MAX_BYTES) {
                return error("too_large", "Screenshot exceeds 2.5 MB PNG payload. Retry with a smaller max_dimension.");
            }
            JsonObject result = new JsonObject();
            result.addProperty("status", "ok");
            result.addProperty("source", "minecraft_main_framebuffer");
            result.addProperty("book_revision", FTBQ2001Revision.compute(ClientQuestFile.INSTANCE));
            result.addProperty("width", width);
            result.addProperty("height", height);
            result.addProperty("original_width", originalWidth);
            result.addProperty("original_height", originalHeight);
            result.addProperty("visible_view_only", true);
            result.addProperty("mime_type", "image/png");
            // Internal transport field: McpHttpServer converts this to MCP ImageContent.
            // Never duplicate base64 inside structuredContent or textual metadata.
            result.addProperty("_image_base64", Base64.getEncoder().encodeToString(png));
            return result;
        } catch (IOException | RuntimeException exception) {
            return error("failed", "Could not capture quest screen: " + exception.getClass().getSimpleName());
        }
    }

    private static double spacing(JsonObject args) {
        return args.has("minimum_spacing") ? args.get("minimum_spacing").getAsDouble() : 2.0;
    }

    private static JsonObject error(String status, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("status", status);
        response.addProperty("message", message);
        return response;
    }

    private static String string(JsonObject source, String key) {
        return source.has(key) && !source.get(key).isJsonNull() ? source.get(key).getAsString() : "";
    }

    private static String bounded(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...[truncated]";
    }
}
