package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.task.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Read-only, paginated chapter inventory for agents. No raw SNBT is included:
 * call get_object for specific data that really requires round-tripping.
 */
public final class FTBQ2001CompactSnapshot {
    private static final int MAX_QUESTS_PER_PAGE = 30;
    private static final int MAX_OBJECTS_PER_QUEST = 32;
    private static final int MAX_DEPENDENCIES = 32;

    private FTBQ2001CompactSnapshot() {}

    public static JsonObject read(JsonObject request) {
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", 1);
        result.addProperty("source", "ftbq_2001_client_read_only");
        if (!ClientQuestFile.exists()) return status(result, "unavailable", "FTB Quests client book not loaded");
        if (request == null || !request.has("chapter_id")
                || !request.get("chapter_id").isJsonPrimitive()
                || !request.get("chapter_id").getAsJsonPrimitive().isString()) {
            return status(result, "invalid_argument", "chapter_id is required");
        }
        String chapterId = request.get("chapter_id").getAsString();
        Chapter chapter;
        try {
            chapter = ClientQuestFile.INSTANCE.getChapter(QuestObjectBase.parseCodeString(chapterId));
        } catch (RuntimeException error) {
            return status(result, "invalid_argument", "Invalid chapter_id");
        }
        if (chapter == null) return status(result, "not_found", "Chapter does not exist");
        int offset;
        int limit;
        try {
            offset = integer(request, "offset", 0, 100000, 0);
            limit = integer(request, "limit", 1, MAX_QUESTS_PER_PAGE, 20);
        } catch (RuntimeException error) {
            return status(result, "invalid_argument", error.getMessage());
        }
        List<Quest> quests = new ArrayList<>(chapter.getQuests());
        int end = Math.min(quests.size(), offset + limit);
        JsonArray page = new JsonArray();
        for (int i = Math.min(offset, quests.size()); i < end; i++) {
            Quest quest = quests.get(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", quest.getCodeString());
            entry.addProperty("title", quest.getTitle().getString());
            entry.addProperty("x", quest.getX());
            entry.addProperty("y", quest.getY());

            List<String> deps = quest.streamDependencies().map(Quest::getCodeString).toList();
            JsonArray dependencies = new JsonArray();
            deps.stream().limit(MAX_DEPENDENCIES).forEach(dependencies::add);
            entry.add("dependencies", dependencies);
            entry.addProperty("dependency_count", deps.size());
            entry.addProperty("dependencies_truncated", deps.size() > MAX_DEPENDENCIES);

            JsonArray tasks = new JsonArray();
            Collection<Task> allTasks = quest.getTasks();
            allTasks.stream().limit(MAX_OBJECTS_PER_QUEST).forEach(t -> {
                JsonObject item = new JsonObject();
                item.addProperty("id", t.getCodeString());
                item.addProperty("type_id", t.getType().getTypeId().toString());
                item.addProperty("title", t.getTitle().getString());
                tasks.add(item);
            });
            entry.add("tasks", tasks);
            entry.addProperty("task_count", allTasks.size());
            entry.addProperty("tasks_truncated", allTasks.size() > MAX_OBJECTS_PER_QUEST);

            JsonArray rewards = new JsonArray();
            Collection<Reward> allRewards = quest.getRewards();
            allRewards.stream().limit(MAX_OBJECTS_PER_QUEST).forEach(r -> {
                JsonObject item = new JsonObject();
                item.addProperty("id", r.getCodeString());
                item.addProperty("type_id", r.getType().getTypeId().toString());
                item.addProperty("title", r.getTitle().getString());
                rewards.add(item);
            });
            entry.add("rewards", rewards);
            entry.addProperty("reward_count", allRewards.size());
            entry.addProperty("rewards_truncated", allRewards.size() > MAX_OBJECTS_PER_QUEST);

            page.add(entry);
        }
        result.addProperty("status", "ok");
        result.addProperty("book_revision", FTBQ2001Revision.compute(ClientQuestFile.INSTANCE));
        result.addProperty("can_edit", ClientQuestFile.INSTANCE.canEdit());
        result.addProperty("chapter_id", chapter.getCodeString());
        result.addProperty("chapter_title", chapter.getTitle().getString());
        result.addProperty("total_quests", quests.size());
        result.add("quests", page);
        result.addProperty("next_offset", end);
        result.addProperty("has_more", end < quests.size());
        result.addProperty("raw_snbt_included", false);
        result.addProperty("note", "Truncation flags are per quest; use get_quest/get_object to inspect full data on demand.");
        return result;
    }

    private static int integer(JsonObject object, String key, int min, int max, int fallback) {
        if (!object.has(key)) return fallback;
        var primitive = object.get(key);
        if (!primitive.isJsonPrimitive() || !primitive.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        double number = primitive.getAsDouble();
        if (!Double.isFinite(number) || Math.rint(number) != number || number < min || number > max) {
            throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max);
        }
        return (int) number;
    }

    private static JsonObject status(JsonObject result, String status, String message) {
        result.addProperty("status", status);
        result.addProperty("message", message);
        return result;
    }
}
