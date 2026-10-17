package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonObject;
import dev.autoftbq.mcp.compat.ftbq.v2001.mixin.QuestScreenAccessor;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestScreen;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;

import java.util.UUID;

/**
 * Temporary, reversible viewport framing in native FTBQ. Runs exclusively on
 * the Minecraft client thread: the MCP HTTP worker never waits on this session.
 *
 * One screenshot at a time, and only for an *already open* quest screen.
 * The GUI is returned to its original chapter/zoom/scroll even on error.
 * Waiting two full client ticks avoids accidentally capturing the old frame.
 */
public final class FTBQ2001CaptureSession {
    private static final int SETTLE_TICKS = 3;
    private static final long MAX_CAPTURE_NANOS = 12_000_000_000L;
    private static final long RESULT_TTL_NANOS = 60_000_000_000L;
    private static Session running;
    private static Completed completed;

    private FTBQ2001CaptureSession() {}

    public static JsonObject begin(JsonObject args) {
        if (!ClientQuestFile.exists()) return error("unavailable", "FTB Quests is not loaded");
        if (running != null) return error("conflict", "Another viewport capture is already running");
        QuestScreen screen = activeScreen();
        if (screen == null) return error("unavailable", "Open the FTB Quests quest book to enable native chapter framing");
        if (screen.isViewingQuest()) return error("unavailable", "Close quest details before framing another chapter");

        String chapterId = string(args, "chapter_id");
        if (chapterId.isBlank()) return error("invalid_argument", "chapter_id is required");
        int maxDimension = args.has("max_dimension") ? args.get("max_dimension").getAsInt() : 1600;
        if (maxDimension < 512 || maxDimension > 2048) return error("invalid_argument", "max_dimension must be 512..2048");
        boolean requireFull = args.has("require_full_chapter") && args.get("require_full_chapter").getAsBoolean();
        Chapter target;
        try {
            target = ClientQuestFile.INSTANCE.getChapter(QuestObjectBase.parseCodeString(chapterId));
        } catch (RuntimeException ignored) {
            return error("invalid_argument", "Invalid chapter ID");
        }
        if (target == null) return error("not_found", "Chapter not found");
        if (target.getQuests().size() > 512) return error("too_large", "Chapter exceeds the 512 quest design inspection limit");

        Session session = new Session(
                UUID.randomUUID().toString(), screen,
                ((QuestScreenAccessor) screen).autoftbq$getSelectedChapter(),
                screen.getZoom(), screen.questPanel.getScrollX(), screen.questPanel.getScrollY(),
                FTBQ2001Revision.compute(ClientQuestFile.INSTANCE), target, maxDimension, requireFull
        );
        try {
            // Keep FTBQ's genuine icons, themed chapter images and dependency lines.
            screen.selectChapter(target);
            // Determine the maximum zoom for which all native widgets fit, if
            // possible. FTBQ 2001 clamps zoom to 4..28.
            screen.addZoom((4 - screen.getZoom()) / 4.0);
            screen.questPanel.alignWidgets();
            double requiredWidth = ((QuestScreenAccessor) screen).autoftbq$getScrollWidth();
            double requiredHeight = ((QuestScreenAccessor) screen).autoftbq$getScrollHeight();
            double availableWidth = Math.max(1, screen.questPanel.width - 24);
            double availableHeight = Math.max(1, screen.questPanel.height - 24);
            double ratio = Math.min(availableWidth / Math.max(1, requiredWidth),
                    availableHeight / Math.max(1, requiredHeight));
            int zoom = Math.max(4, Math.min(28, (int) Math.floor(4 * ratio)));
            screen.addZoom((zoom - screen.getZoom()) / 4.0);
            screen.questPanel.resetScroll();
            requiredWidth = ((QuestScreenAccessor) screen).autoftbq$getScrollWidth();
            requiredHeight = ((QuestScreenAccessor) screen).autoftbq$getScrollHeight();
            session.fullChapterFits = requiredWidth <= availableWidth && requiredHeight <= availableHeight;
            session.zoomUsed = screen.getZoom();

            if (requireFull && !session.fullChapterFits) {
                restore(session);
                return error("too_large", "Chapter cannot fit into the native screen at minimum FTBQ zoom. Use ftbq.render_chapter_map for a complete schematic preview.");
            }
            running = session;
            completed = null;
            JsonObject result = new JsonObject();
            result.addProperty("status", "pending");
            result.addProperty("capture_id", session.id);
            result.addProperty("chapter_id", target.getCodeString());
            result.addProperty("full_chapter_fits", session.fullChapterFits);
            result.addProperty("zoom_used", session.zoomUsed);
            result.addProperty("message", "Native chapter selected; call ftbq.get_capture_result with capture_id after the next rendered frames.");
            return result;
        } catch (RuntimeException failure) {
            restore(session);
            return error("failed", "Native chapter framing failed: " + failure.getClass().getSimpleName());
        }
    }

    /** Called at the end of client ticks, never from the MCP worker thread. */
    public static void onClientTick() {
        Session session = running;
        if (session == null) {
            expireResult();
            return;
        }
        if (System.nanoTime() - session.started > MAX_CAPTURE_NANOS) {
            finish(session, error("unavailable", "Native preview timed out"));
            return;
        }
        if (activeScreen() != session.screen) {
            finish(session, error("unavailable", "The player closed or changed the FTBQ screen during capture"));
            return;
        }
        if (!ClientQuestFile.exists() || !session.bookRevision.equals(FTBQ2001Revision.compute(ClientQuestFile.INSTANCE))) {
            finish(session, error("conflict", "The book changed while its screenshot was being prepared"));
            return;
        }
        if (++session.ticks < SETTLE_TICKS) return;
        JsonObject result = new JsonObject();
        try {
            result = FTBQ2001VisualService.captureView(session.imageArguments());
            if ("ok".equals(string(result, "status"))) {
                result.addProperty("capture_id", session.id);
                result.addProperty("chapter_id", session.target.getCodeString());
                result.addProperty("full_chapter_fits", session.fullChapterFits);
                result.addProperty("zoom_used", session.zoomUsed);
                result.addProperty("temporarily_changed_view", true);
                result.addProperty("view_restored", true);
            }
        } catch (RuntimeException error) {
            result = error("failed", "Native screenshot failed: " + error.getClass().getSimpleName());
        }
        finish(session, result);
    }

    public static JsonObject result(JsonObject args) {
        String id = string(args, "capture_id");
        if (id.isBlank()) return error("invalid_argument", "capture_id is required");
        expireResult();
        if (running != null && running.id.equals(id)) {
            JsonObject pending = new JsonObject();
            pending.addProperty("status", "pending");
            pending.addProperty("capture_id", id);
            return pending;
        }
        if (completed != null && completed.id.equals(id)) return completed.result.deepCopy();
        return error("not_found", "Unknown or expired capture_id");
    }

    public static JsonObject cancel(JsonObject args) {
        String id = string(args, "capture_id");
        if (id.isBlank()) return error("invalid_argument", "capture_id is required");
        if (running != null && running.id.equals(id)) {
            finish(running, error("cancelled", "Capture was cancelled before the image was taken"));
            JsonObject value = new JsonObject();
            value.addProperty("status", "ok");
            value.addProperty("capture_id", id);
            value.addProperty("cancelled", true);
            return value;
        }
        return error("not_found", "No active capture with that ID");
    }

    private static void finish(Session session, JsonObject result) {
        try {
            boolean restored = restore(session);
            if ("ok".equals(string(result, "status"))) result.addProperty("view_restored", restored);
        } finally {
            running = null;
            completed = new Completed(session.id, result, System.nanoTime());
        }
    }

    private static boolean restore(Session session) {
        // Don't forcibly reopen the screen if the player intentionally closed it.
        if (activeScreen() != session.screen) return false;
        try {
            session.screen.selectChapter(session.originalChapter);
            session.screen.addZoom((session.originalZoom - session.screen.getZoom()) / 4.0);
            session.screen.questPanel.setScrollX(session.originalScrollX);
            session.screen.questPanel.setScrollY(session.originalScrollY);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static QuestScreen activeScreen() {
        if (!ClientQuestFile.exists()) return null;
        QuestScreen current = ClientUtils.getCurrentGuiAs(QuestScreen.class);
        QuestScreen owned = ClientQuestFile.INSTANCE.getQuestScreen().orElse(null);
        return current == owned ? current : null;
    }

    private static void expireResult() {
        if (completed != null && System.nanoTime() - completed.finishedAt > RESULT_TTL_NANOS) {
            completed = null;
        }
    }

    private static JsonObject error(String status, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("message", message);
        return result;
    }

    private static String string(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private static final class Session {
        final String id;
        final QuestScreen screen;
        final Chapter originalChapter, target;
        final int originalZoom, maxDimension;
        final double originalScrollX, originalScrollY;
        final String bookRevision;
        final boolean requireFull;
        final long started = System.nanoTime();
        int ticks = 0, zoomUsed = 4;
        boolean fullChapterFits;
        Session(String id, QuestScreen screen, Chapter originalChapter,
                int originalZoom, double originalScrollX, double originalScrollY,
                String bookRevision, Chapter target, int maxDimension, boolean requireFull) {
            this.id = id; this.screen = screen; this.originalChapter = originalChapter;
            this.originalZoom = originalZoom;
            this.originalScrollX = originalScrollX; this.originalScrollY = originalScrollY;
            this.bookRevision = bookRevision; this.target = target;
            this.maxDimension = maxDimension; this.requireFull = requireFull;
        }
        JsonObject imageArguments() {
            JsonObject value = new JsonObject();
            value.addProperty("max_dimension", maxDimension);
            return value;
        }
    }

    private record Completed(String id, JsonObject result, long finishedAt) {}
}
