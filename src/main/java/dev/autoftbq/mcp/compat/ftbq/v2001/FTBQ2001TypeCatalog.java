package dev.autoftbq.mcp.compat.ftbq.v2001;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ftb.mods.ftbquests.client.ClientQuestFile;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObjectBase;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class FTBQ2001TypeCatalog {
    private FTBQ2001TypeCatalog() {}

    public static JsonObject list(String kind) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("kind", kind);
        JsonArray entries = new JsonArray();
        if ("task".equals(kind)) {
            for (Map.Entry<ResourceLocation, TaskType> entry : TaskTypes.TYPES.entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("type_id", entry.getKey().toString());
                v.addProperty("display_name", entry.getValue().getDisplayName().getString());
                entries.add(v);
            }
        } else if ("reward".equals(kind)) {
            for (Map.Entry<ResourceLocation, RewardType> entry : RewardTypes.TYPES.entrySet()) {
                JsonObject v = new JsonObject();
                v.addProperty("type_id", entry.getKey().toString());
                v.addProperty("display_name", entry.getValue().getDisplayName().getString());
                entries.add(v);
            }
        } else {
            result.addProperty("status", "invalid_argument");
            result.addProperty("message", "kind must be task or reward");
        }
        result.add("types", entries);
        return result;
    }

    public static JsonObject schema(String kind, String typeId) {
        JsonObject result = new JsonObject();
        result.addProperty("kind", kind);
        result.addProperty("type_id", typeId);
        if (!ClientQuestFile.exists()) {
            result.addProperty("status", "unavailable");
            return result;
        }
        ResourceLocation id = ResourceLocation.tryParse(typeId);
        if (id == null) {
            result.addProperty("status", "invalid_argument");
            return result;
        }

        ChapterGroup group = new ChapterGroup(1L, ClientQuestFile.INSTANCE);
        Chapter chapter = new Chapter(2L, ClientQuestFile.INSTANCE, group);
        Quest quest = new Quest(3L, chapter);
        QuestObjectBase value = null;
        if ("task".equals(kind)) {
            TaskType type = TaskTypes.TYPES.get(id);
            if (type != null) value = type.createTask(4L, quest);
        } else if ("reward".equals(kind)) {
            RewardType type = RewardTypes.TYPES.get(id);
            if (type != null) value = type.createReward(4L, quest);
        }
        if (value == null) {
            result.addProperty("status", "not_found");
            return result;
        }

        CompoundTag defaults = new CompoundTag();
        try {
            value.writeData(defaults);
            result.addProperty("defaults_snbt", defaults.toString());
        } catch (Throwable error) {
            result.addProperty("defaults_snbt", "{}");
            result.addProperty("defaults_status", "unavailable");
        }
        result.add("curated_fields", curated(kind, id.getPath()));
        result.addProperty("status", "ok");
        result.addProperty("schema_source", "runtime_defaults+curated_official_metadata");
        result.addProperty("note", "Runtime defaults discover addon types. Curated metadata is richer for known official types; unknown addon fields are preserved through raw SNBT operations.");
        return result;
    }

    private static JsonArray curated(String kind, String path) {
        JsonArray fields = new JsonArray();
        if ("task".equals(kind)) {
            switch (path) {
                case "item" -> { field(fields, "item", "item_stack", "Target item"); field(fields, "count", "long", "Required count"); field(fields, "consume_items", "tristate", "Whether submitted items are consumed"); }
                case "kill" -> { field(fields, "entity", "registry_id", "Entity type"); field(fields, "value", "long", "Kill count"); }
                case "dimension" -> field(fields, "dimension", "registry_id", "Dimension id");
                case "biome" -> field(fields, "biome", "registry_id", "Biome id");
                case "structure" -> field(fields, "structure", "registry_id", "Structure id");
                case "advancement" -> field(fields, "advancement", "registry_id", "Advancement id");
                case "xp" -> { field(fields, "value", "long", "XP amount"); field(fields, "points", "boolean", "Use points instead of levels"); }
                case "checkmark" -> field(fields, "optional_task", "boolean", "Optional task");
                default -> {}
            }
        } else if ("reward".equals(kind)) {
            switch (path) {
                case "item" -> { field(fields, "item", "item_stack", "Reward item"); field(fields, "count", "int", "Reward count"); }
                case "xp" -> field(fields, "xp", "int", "XP points");
                case "xp_levels" -> field(fields, "xp_levels", "int", "XP levels");
                case "command" -> field(fields, "command", "string_list", "Commands executed on claim");
                case "advancement" -> field(fields, "advancement", "registry_id", "Advancement id");
                default -> {}
            }
        }
        return fields;
    }

    private static void field(JsonArray array, String name, String type, String description) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("type", type);
        field.addProperty("description", description);
        array.add(field);
    }
}
