package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.compat.ftbq.v2001.mixin.QuestScreenAccessor;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import net.minecraft.nbt.CompoundTag;

public final class FTBQ2001ReadService {
    private FTBQ2001ReadService() {}

    private static JsonObject unavailable() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unavailable");
        result.addProperty("message", "FTB Quests client quest file is not loaded.");
        return result;
    }

    public static JsonObject context() {
        if (!ClientQuestFile.exists()) return unavailable();
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("book_revision", FTBQ2001Revision.compute(ClientQuestFile.INSTANCE));
        result.addProperty("can_edit", ClientQuestFile.INSTANCE.canEdit());

        JsonObject chapter = new JsonObject();
        JsonArray selected = new JsonArray();
        ClientQuestFile.INSTANCE.getQuestScreen().ifPresent(screen -> {
            Chapter current = ((QuestScreenAccessor) screen).autoftbq$getSelectedChapter();
            if (current != null) {
                chapter.addProperty("id", current.getCodeString());
                chapter.addProperty("title", current.getTitle().getString());
            }
            screen.getSelectedQuests().stream().limit(256).forEach(quest -> {
                JsonObject q = new JsonObject();
                q.addProperty("id", quest.getCodeString());
                q.addProperty("title", quest.getTitle().getString());
                q.addProperty("chapter_id", quest.getChapter().getCodeString());
                q.addProperty("x", quest.getX());
                q.addProperty("y", quest.getY());
                selected.add(q);
            });
        });
        result.add("chapter", chapter);
        result.add("selected_quests", selected);
        return result;
    }

    public static JsonObject book() {
        if (!ClientQuestFile.exists()) return unavailable();
        JsonObject root = new JsonObject();
        root.addProperty("status", "ok");
        root.addProperty("book_revision", FTBQ2001Revision.compute(ClientQuestFile.INSTANCE));
        root.addProperty("can_edit", ClientQuestFile.INSTANCE.canEdit());
        JsonArray groups = new JsonArray();
        ClientQuestFile.INSTANCE.getChapterGroups().forEach(group -> {
            JsonObject g = new JsonObject();
            g.addProperty("id", group.getCodeString());
            g.addProperty("title", group.getTitle().getString());
            JsonArray chapters = new JsonArray();
            group.getChapters().forEach(chapter -> {
                JsonObject c = new JsonObject();
                c.addProperty("id", chapter.getCodeString());
                c.addProperty("title", chapter.getTitle().getString());
                c.addProperty("quest_count", chapter.getQuests().size());
                chapters.add(c);
            });
            g.add("chapters", chapters);
            groups.add(g);
        });
        root.add("chapter_groups", groups);
        return root;
    }

    public static JsonObject chapter(String id) {
        if (!ClientQuestFile.exists()) return unavailable();
        Chapter chapter = ClientQuestFile.INSTANCE.getChapter(QuestObjectBase.parseCodeString(id));
        if (chapter == null) return notFound("chapter", id);
        JsonObject result = objectBase(chapter);
        JsonArray quests = new JsonArray();
        chapter.getQuests().forEach(quest -> {
            JsonObject q = objectBase(quest);
            q.addProperty("x", quest.getX());
            q.addProperty("y", quest.getY());
            q.addProperty("task_count", quest.getTasks().size());
            q.addProperty("reward_count", quest.getRewards().size());
            quests.add(q);
        });
        result.add("quests", quests);
        return result;
    }

    public static JsonObject quest(String id) {
        if (!ClientQuestFile.exists()) return unavailable();
        Quest quest = ClientQuestFile.INSTANCE.getQuest(QuestObjectBase.parseCodeString(id));
        if (quest == null) return notFound("quest", id);
        JsonObject result = objectBase(quest);
        result.addProperty("chapter_id", quest.getChapter().getCodeString());
        result.addProperty("x", quest.getX());
        result.addProperty("y", quest.getY());

        JsonArray dependencies = new JsonArray();
        quest.streamDependencies().limit(256).forEach(q -> dependencies.add(q.getCodeString()));
        result.add("dependencies", dependencies);

        JsonArray tasks = new JsonArray();
        quest.getTasks().forEach(task -> {
            JsonObject value = objectBase(task);
            value.addProperty("type_id", task.getType().getTypeId().toString());
            tasks.add(value);
        });
        result.add("tasks", tasks);

        JsonArray rewards = new JsonArray();
        quest.getRewards().forEach(reward -> {
            JsonObject value = objectBase(reward);
            value.addProperty("type_id", reward.getType().getTypeId().toString());
            rewards.add(value);
        });
        result.add("rewards", rewards);
        return result;
    }

    public static JsonObject object(String id) {
        if (!ClientQuestFile.exists()) return unavailable();
        QuestObjectBase value = ClientQuestFile.INSTANCE.getBase(QuestObjectBase.parseCodeString(id));
        return value == null ? notFound("object", id) : objectBase(value);
    }

    private static JsonObject objectBase(QuestObjectBase value) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("id", value.getCodeString());
        result.addProperty("object_type", value.getObjectType().name());
        result.addProperty("title", value.getTitle().getString());
        CompoundTag data = new CompoundTag();
        value.writeData(data);
        result.addProperty("data_snbt", data.toString());
        return result;
    }

    private static JsonObject notFound(String kind, String id) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "not_found");
        result.addProperty("kind", kind);
        result.addProperty("id", id);
        return result;
    }
}
