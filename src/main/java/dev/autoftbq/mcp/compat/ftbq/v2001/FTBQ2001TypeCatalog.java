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
                JsonObject value = new JsonObject();
                value.addProperty("type_id", entry.getKey().toString());
                value.addProperty("display_name", entry.getValue().getDisplayName().getString());
                value.addProperty("namespace", entry.getKey().getNamespace());
                value.addProperty("curated", curated("task", entry.getKey().getPath()).size() > 0);
                entries.add(value);
            }
        } else if ("reward".equals(kind)) {
            for (Map.Entry<ResourceLocation, RewardType> entry : RewardTypes.TYPES.entrySet()) {
                JsonObject value = new JsonObject();
                value.addProperty("type_id", entry.getKey().toString());
                value.addProperty("display_name", entry.getValue().getDisplayName().getString());
                value.addProperty("namespace", entry.getKey().getNamespace());
                value.addProperty("curated", curated("reward", entry.getKey().getPath()).size() > 0);
                entries.add(value);
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
            result.addProperty("defaults_status", "ok");
        } catch (Throwable error) {
            result.addProperty("defaults_snbt", "{}");
            result.addProperty("defaults_status", "unavailable");
        }

        JsonArray curated = curated(kind, id.getPath());
        result.add("curated_fields", curated);
        result.addProperty("curated", !curated.isEmpty());
        result.addProperty("status", "ok");
        result.addProperty("schema_source",
                curated.isEmpty() ? "runtime_defaults" : "runtime_defaults+curated_official_metadata");
        result.addProperty("note",
                "Runtime defaults discover addon types. Curated metadata is richer for known official types; "
                        + "unknown addon fields remain writable through the generic raw-SNBT path.");
        return result;
    }

    private static JsonArray curated(String kind, String path) {
        JsonArray fields = new JsonArray();
        if ("task".equals(kind)) {
            switch (path) {
                case "item" -> {
                    field(fields, "item", "item_stack", "Target item", "item");
                    field(fields, "count", "long", "Required count");
                    field(fields, "consume_items", "tristate", "Whether submitted items are consumed");
                    field(fields, "only_from_crafting", "tristate", "Require items obtained from crafting");
                    field(fields, "match_components", "choice", "Item component matching mode");
                    field(fields, "task_screen_only", "boolean", "Only test while the quest screen is open");
                }
                case "custom" -> {
                    field(fields, "title", "string", "Custom task title");
                    field(fields, "description", "string_list", "Custom task description");
                    field(fields, "icon", "icon", "Custom task icon");
                    field(fields, "max_progress", "long", "Maximum custom progress");
                }
                case "xp" -> {
                    field(fields, "value", "long", "Required XP amount");
                    field(fields, "points", "boolean", "Use XP points instead of levels");
                }
                case "dimension" -> field(fields, "dimension", "registry_id", "Dimension id", "dimension");
                case "stat" -> {
                    field(fields, "stat", "registry_id", "Custom statistic id", "stat");
                    field(fields, "value", "int", "Required statistic value");
                }
                case "kill" -> {
                    field(fields, "entity", "registry_id", "Entity type", "entity");
                    field(fields, "entityTypeTag", "registry_id", "Optional entity-type tag", "entity_tag");
                    field(fields, "value", "long", "Kill count");
                    field(fields, "custom_name", "string", "Optional custom entity name");
                    field(fields, "nbt_filter", "snbt", "Optional entity NBT filter");
                }
                case "location" -> {
                    field(fields, "dimension", "registry_id", "Dimension id", "dimension");
                    field(fields, "ignore_dimension", "boolean", "Ignore dimension while checking position");
                    field(fields, "position", "int_array", "Center position");
                    field(fields, "size", "int_array", "Allowed area size");
                }
                case "checkmark" -> {
                    // common fields only
                }
                case "advancement" -> {
                    field(fields, "advancement", "registry_id", "Advancement id", "advancement");
                    field(fields, "criterion", "string", "Optional advancement criterion");
                }
                case "observation" -> {
                    field(fields, "to_observe", "string", "Observed target");
                    field(fields, "observation_type", "choice",
                            "Observation mode: block, block_tag, block_state, block_entity, block_entity_type, entity_type or entity_type_tag");
                    field(fields, "observe_type", "int", "Legacy observation type");
                    field(fields, "timer", "long", "Required observation time");
                }
                case "biome" -> field(fields, "biome", "registry_id", "Biome id", "biome");
                case "structure" -> field(fields, "structure", "registry_id", "Structure id", "structure");
                case "gamestage" -> {
                    field(fields, "stage", "string", "GameStage id");
                    field(fields, "team_stage", "boolean", "Check the team's stage instead of player stage");
                }
                case "fluid" -> field(fields, "fluid", "fluid_stack", "Required fluid", "fluid");
                case "forge_energy", "tech_reborn_energy" -> {
                    field(fields, "value", "long", "Required energy");
                    field(fields, "max_input", "long", "Maximum accepted energy per input");
                }
                default -> {}
            }
            commonTaskFields(fields);
        } else if ("reward".equals(kind)) {
            switch (path) {
                case "item" -> {
                    field(fields, "item", "item_stack", "Reward item", "item");
                    field(fields, "count", "int", "Reward count");
                    field(fields, "random_bonus", "int", "Random bonus count");
                    field(fields, "only_one", "boolean", "Only grant one item choice");
                }
                case "choice", "all_table" -> field(fields, "table_id", "object_id", "Reward-table id", "reward_table");
                case "random", "loot" -> {
                    field(fields, "table_id", "object_id", "Reward-table id", "reward_table");
                    field(fields, "table_data", "compound", "Embedded reward-table data");
                }
                case "command" -> {
                    field(fields, "command", "string_list", "Server command(s) executed on claim");
                    field(fields, "permission_level", "int", "Command permission level");
                    field(fields, "silent", "boolean", "Suppress command feedback");
                    field(fields, "feedback_message", "string", "Optional feedback message");
                }
                case "custom" -> {
                    field(fields, "title", "string", "Custom reward title");
                    field(fields, "description", "string_list", "Custom reward description");
                    field(fields, "icon", "icon", "Custom reward icon");
                }
                case "xp" -> field(fields, "xp", "int", "XP points");
                case "xp_levels" -> field(fields, "xp_levels", "int", "XP levels");
                case "advancement" -> {
                    field(fields, "advancement", "registry_id", "Advancement id", "advancement");
                    field(fields, "criterion", "string", "Optional criterion");
                }
                case "toast" -> field(fields, "description", "string", "Toast text");
                case "gamestage" -> {
                    field(fields, "stage", "string", "GameStage id");
                    field(fields, "remove", "boolean", "Remove rather than grant the stage");
                }
                case "currency" -> field(fields, "amount", "int", "Currency amount");
                default -> {}
            }
            commonRewardFields(fields);
        }
        return fields;
    }

    private static void commonTaskFields(JsonArray fields) {
        field(fields, "optional_task", "boolean", "Task is optional");
        field(fields, "disable_toast", "boolean", "Disable completion toast");
        field(fields, "tags", "string_list", "Object tags");
    }

    private static void commonRewardFields(JsonArray fields) {
        field(fields, "team_reward", "tristate", "Share reward with team");
        field(fields, "auto", "choice", "Automatic claim mode");
        field(fields, "exclude_from_claim_all", "boolean", "Exclude from claim-all");
        field(fields, "ignore_reward_blocking", "boolean", "Ignore reward blocking");
        field(fields, "disable_reward_screen_blur", "boolean", "Disable reward-screen blur");
        field(fields, "disable_toast", "boolean", "Disable reward toast");
        field(fields, "tags", "string_list", "Object tags");
    }

    private static void field(JsonArray array, String name, String type, String description) {
        field(array, name, type, description, null);
    }

    private static void field(JsonArray array, String name, String type,
                              String description, String registry) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("type", type);
        field.addProperty("description", description);
        if (registry != null && !registry.isBlank()) field.addProperty("registry", registry);
        array.add(field);
    }
}
