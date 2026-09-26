package dev.autoftbq.mcp.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class McpToolCatalog {
    private McpToolCatalog() {}

    public static List<ToolDefinition> all() {
        List<ToolDefinition> tools = new ArrayList<>();

        tools.add(tool("autoftbq.health",
                "Report Minecraft/loader/FTB Quests compatibility, adapter capabilities and server edit permission.",
                obj(), List.of()));

        tools.add(tool("minecraft.capabilities",
                "Describe live game-data coverage and explicit unknown areas. Call this before claiming complete modpack coverage.",
                obj(), List.of()));
        tools.add(tool("minecraft.search_registry",
                "Search live Minecraft/FTB-synchronized registry data without guessing ids. Current 2001 adapter covers item, block, entity, fluid, mob_effect, stat, recipe_type, biome, structure, dimension and advancement.",
                obj(
                        p("registry", str("Registry kind")),
                        p("query", str("Case-insensitive id substring")),
                        p("namespace", str("Optional namespace")),
                        p("offset", integer("Pagination offset")),
                        p("limit", integer("1-100 results")),
                        p("data_version", str("Optional version from an earlier page"))
                ), List.of("registry")));
        tools.add(tool("minecraft.validate_ids",
                "Validate exact registry ids against the live game or authoritative server adapter. Current 2001 coverage includes item, block, entity, fluid, mob_effect, stat, biome, structure, dimension and advancement.",
                obj(
                        p("registry", str("Registry kind supported by minecraft.capabilities")),
                        p("ids", array(str("Exact resource ids")))
                ), List.of("registry", "ids")));
        tools.add(tool("minecraft.inspect_item",
                "Inspect one live item id with tags and rendering evidence. Registration is not treated as proof of survival obtainability.",
                obj(p("item_id", str("Exact item id"))), List.of("item_id")));
        tools.add(tool("minecraft.search_recipes",
                "Search recipes that consume or produce an exact live item id. Results are paged and bounded.",
                obj(
                        p("item_id", str("Exact item id")),
                        p("direction", str("input, output or both")),
                        p("offset", integer("Pagination offset")),
                        p("limit", integer("1-50 matches"))
                ), List.of("item_id")));
        tools.add(tool("minecraft.inspect_resource",
                "Read or list server datapack resources currently covered by the adapter: loot_tables and advancements. Runtime script overrides remain unknown.",
                obj(
                        p("dataset", str("loot_tables or advancements")),
                        p("id", str("Optional exact resource id")),
                        p("query", str("Optional id substring for listing")),
                        p("namespace", str("Optional namespace")),
                        p("offset", integer("Pagination offset")),
                        p("limit", integer("1-100 results")),
                        p("data_version", str("Optional version from an earlier page"))
                ), List.of("dataset")));

        tools.add(tool("ftbq.get_context",
                "Read the FTB Quests screen context visible to the local player: current chapter, selected quests, client revision and edit state.",
                obj(), List.of()));
        tools.add(tool("ftbq.get_book",
                "Read the live quest-book map: chapter groups, chapters, counts, edit state and revision.",
                obj(), List.of()));
        tools.add(tool("ftbq.get_chapter",
                "Read one chapter and its quest summaries from the live client quest book.",
                obj(p("id", str("FTBQ object code string"))), List.of("id")));
        tools.add(tool("ftbq.get_quest",
                "Read one quest including dependencies, tasks, rewards and raw SNBT.",
                obj(p("id", str("FTBQ object code string"))), List.of("id")));
        tools.add(tool("ftbq.get_object",
                "Read any FTBQ object by code string with its raw SNBT.",
                obj(p("id", str("FTBQ object code string"))), List.of("id")));
        tools.add(tool("ftbq.list_task_types",
                "Enumerate task types actually registered in this runtime, including compatible addon types.",
                obj(), List.of()));
        tools.add(tool("ftbq.list_reward_types",
                "Enumerate reward types actually registered in this runtime, including compatible addon types.",
                obj(), List.of()));
        tools.add(tool("ftbq.get_type_schema",
                "Inspect one runtime task/reward type. Returns runtime defaults plus richer curated metadata for known official types.",
                obj(
                        p("kind", str("task or reward")),
                        p("type_id", str("Exact registered type id"))
                ), List.of("kind", "type_id")));

        JsonObject writeBase = obj(
                p("expected_revision", str("Revision previously read from ftbq.get_context/get_book")),
                p("proposal_id", str("Optional idempotency key; generate a stable UUID when retrying the same write"))
        );

        tools.add(tool("ftbq.create_chapter_group",
                "Create one chapter group. The returned temporary-id map can be used by a low-level atomic batch.",
                merge(writeBase, obj(
                        p("temp_id", str("Optional temporary id")),
                        p("title", str("Chapter-group title"))
                )), List.of("expected_revision", "title")));
        tools.add(tool("ftbq.create_reward_table",
                "Create one independent FTB Quests reward table.",
                merge(writeBase, obj(
                        p("temp_id", str("Optional temporary id")),
                        p("title", str("Optional reward-table title"))
                )), List.of("expected_revision")));

        tools.add(tool("ftbq.create_chapter",
                "Create one chapter as an immediately committed, rollback-safe server transaction.",
                merge(writeBase, obj(
                        p("temp_id", str("Optional temporary id for references inside this same batch")),
                        p("group_id", str("Optional chapter-group id; defaults to the default group")),
                        p("title", str("Chapter title")),
                        p("subtitle", str("Optional subtitle")),
                        p("icon", str("Optional item id icon"))
                )), List.of("expected_revision", "title")));
        tools.add(tool("ftbq.create_quest",
                "Create one quest and broadcast it live after the server transaction commits.",
                merge(writeBase, obj(
                        p("temp_id", str("Optional temporary id")),
                        p("chapter_id", str("Real chapter id or temp id in the same batch")),
                        p("title", str("Quest title")),
                        p("subtitle", str("Optional subtitle")),
                        p("description", stringOrArray("Description string or string array")),
                        p("icon", str("Optional item id icon")),
                        p("x", number("Quest x coordinate")),
                        p("y", number("Quest y coordinate"))
                )), List.of("expected_revision", "chapter_id", "title", "x", "y")));
        tools.add(tool("ftbq.update_chapter",
                "Patch common chapter fields while preserving quests and other chapter data.",
                merge(writeBase, obj(
                        p("chapter_id", str("Chapter id")),
                        p("changes", objectAny("Allowed keys: title, subtitle, icon"))
                )), List.of("expected_revision", "chapter_id", "changes")));
        tools.add(tool("ftbq.move_chapter_to_group",
                "Move a chapter between chapter groups without changing its id or quests.",
                merge(writeBase, obj(
                        p("chapter_id", str("Chapter id")),
                        p("group_id", str("Target chapter-group id"))
                )), List.of("expected_revision", "chapter_id", "group_id")));

        tools.add(tool("ftbq.update_quest",
                "Patch safe common quest fields. The server rejects stale revisions and rolls back the whole operation on failure.",
                merge(writeBase, obj(
                        p("quest_id", str("Quest id")),
                        p("changes", objectAny("Allowed keys: title, subtitle, description, icon, x, y"))
                )), List.of("expected_revision", "quest_id", "changes")));
        tools.add(tool("ftbq.move_quest",
                "Move an existing quest to another chapter and/or exact canvas coordinates while preserving its id, tasks and rewards.",
                merge(writeBase, obj(
                        p("quest_id", str("Quest id")),
                        p("chapter_id", str("Optional target chapter id")),
                        p("x", number("Optional x coordinate")),
                        p("y", number("Optional y coordinate"))
                )), List.of("expected_revision", "quest_id")));

        tools.add(tool("ftbq.add_task",
                "Add a task. Official item/checkmark/xp helpers accept semantic fields; any runtime task type can be added with data_snbt.",
                merge(writeBase, obj(
                        p("quest_id", str("Quest id")),
                        p("type_id", str("Registered type id, e.g. ftbquests:item")),
                        p("item_id", str("For item task")),
                        p("count", integer("For item task")),
                        p("amount", integer("For xp task")),
                        p("data_snbt", str("Raw SNBT for arbitrary registered task types"))
                )), List.of("expected_revision", "quest_id", "type_id")));
        tools.add(tool("ftbq.add_reward",
                "Add a reward. Official item/xp/xp_levels helpers accept semantic fields; any runtime reward type can be added with data_snbt.",
                merge(writeBase, obj(
                        p("quest_id", str("Quest id")),
                        p("type_id", str("Registered reward type id")),
                        p("item_id", str("For item reward")),
                        p("count", integer("For item reward")),
                        p("amount", integer("For xp/xp_levels reward")),
                        p("data_snbt", str("Raw SNBT for arbitrary registered reward types"))
                )), List.of("expected_revision", "quest_id", "type_id")));
        tools.add(tool("ftbq.move_quest_object",
                "Reorder one task or reward to an exact zero-based index in its quest.",
                merge(writeBase, obj(
                        p("quest_id", str("Owning quest id")),
                        p("object_kind", str("task or reward")),
                        p("object_id", str("Task or reward id")),
                        p("new_index", integer("Exact zero-based destination index"))
                )), List.of("expected_revision", "quest_id", "object_kind", "object_id", "new_index")));

        tools.add(tool("ftbq.remove_quest_object",
                "Remove one task or reward inside a rollback-safe transaction.",
                merge(writeBase, obj(p("object_id", str("Task or reward id")))),
                List.of("expected_revision", "object_id")));
        tools.add(tool("ftbq.delete_quest",
                "Delete one quest and its children inside a rollback-safe transaction.",
                merge(writeBase, obj(p("quest_id", str("Quest id")))),
                List.of("expected_revision", "quest_id")));
        tools.add(tool("ftbq.delete_chapter",
                "Delete one chapter and its children inside a rollback-safe transaction.",
                merge(writeBase, obj(p("chapter_id", str("Chapter id")))),
                List.of("expected_revision", "chapter_id")));
        tools.add(tool("ftbq.delete_chapter_group",
                "Delete a non-default chapter group. FTBQ moves its chapters back to the default group.",
                merge(writeBase, obj(p("group_id", str("Chapter-group id")))),
                List.of("expected_revision", "group_id")));
        tools.add(tool("ftbq.delete_reward_table",
                "Delete one independent reward table inside the same rollback-safe transaction system.",
                merge(writeBase, obj(p("reward_table_id", str("Reward-table id")))),
                List.of("expected_revision", "reward_table_id")));

        tools.add(tool("ftbq.connect_quests",
                "Make quest_id depend on dependency_id.",
                merge(writeBase, obj(
                        p("quest_id", str("Dependent quest id")),
                        p("dependency_id", str("Prerequisite quest id"))
                )), List.of("expected_revision", "quest_id", "dependency_id")));
        tools.add(tool("ftbq.disconnect_quests",
                "Remove one prerequisite edge between two quests.",
                merge(writeBase, obj(
                        p("quest_id", str("Dependent quest id")),
                        p("dependency_id", str("Prerequisite quest id"))
                )), List.of("expected_revision", "quest_id", "dependency_id")));

        tools.add(tool("ftbq.apply_dependency_plan",
                "Atomically apply up to 40 dependency edges.",
                merge(writeBase, obj(p("edges", array(objectAny("Each edge has quest_id and dependency_id"))))),
                List.of("expected_revision", "edges")));
        tools.add(tool("ftbq.apply_operations",
                "Advanced low-level atomic transaction entry point. Operations use the version-neutral AutoFTBQ operation contract; prefer semantic tools when possible.",
                merge(writeBase, obj(p("operations", array(objectAny("Operation objects"))))),
                List.of("expected_revision", "operations")));
        tools.add(tool("ftbq.undo_last",
                "Undo the specified last AutoFTBQ proposal only if the quest book has not changed since it committed.",
                obj(
                        p("proposal_id", str("Proposal id returned by a successful write")),
                        p("expected_revision", str("Current post-write revision"))
                ), List.of("proposal_id", "expected_revision")));

        tools.add(tool("ftbq.transaction_begin",
                "Open a staged transaction at the current server revision. Staged changes are not visible until commit.",
                obj(), List.of()));
        tools.add(tool("ftbq.transaction_stage",
                "Append low-level operations to a staged transaction without touching the live quest book.",
                obj(
                        p("transaction_id", str("Transaction id")),
                        p("operations", array(objectAny("Operation objects")))
                ), List.of("transaction_id", "operations")));
        tools.add(tool("ftbq.transaction_status",
                "Inspect one staged transaction.",
                obj(p("transaction_id", str("Transaction id"))), List.of("transaction_id")));
        tools.add(tool("ftbq.transaction_commit",
                "Atomically commit a staged transaction against its original revision, with snapshot rollback and live broadcast.",
                obj(p("transaction_id", str("Transaction id"))), List.of("transaction_id")));
        tools.add(tool("ftbq.transaction_abort",
                "Discard a staged transaction without changing the quest book.",
                obj(p("transaction_id", str("Transaction id"))), List.of("transaction_id")));

        return List.copyOf(tools);
    }

    private static ToolDefinition tool(String name, String description, JsonObject props, List<String> required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", props);
        JsonArray req = new JsonArray();
        required.forEach(req::add);
        schema.add("required", req);
        schema.addProperty("additionalProperties", false);
        return new ToolDefinition(name, description, schema);
    }

    private static JsonObject obj(JsonObject... properties) {
        JsonObject result = new JsonObject();
        for (JsonObject value : properties) {
            for (String key : value.keySet()) result.add(key, value.get(key));
        }
        return result;
    }

    private static JsonObject p(String name, JsonObject schema) {
        JsonObject result = new JsonObject();
        result.add(name, schema);
        return result;
    }

    private static JsonObject merge(JsonObject a, JsonObject b) {
        JsonObject result = a.deepCopy();
        for (String key : b.keySet()) result.add(key, b.get(key));
        return result;
    }

    private static JsonObject str(String description) {
        JsonObject v = new JsonObject(); v.addProperty("type", "string"); v.addProperty("description", description); return v;
    }
    private static JsonObject integer(String description) {
        JsonObject v = new JsonObject(); v.addProperty("type", "integer"); v.addProperty("description", description); return v;
    }
    private static JsonObject number(String description) {
        JsonObject v = new JsonObject(); v.addProperty("type", "number"); v.addProperty("description", description); return v;
    }
    private static JsonObject array(JsonObject items) {
        JsonObject v = new JsonObject(); v.addProperty("type", "array"); v.add("items", items); return v;
    }
    private static JsonObject objectAny(String description) {
        JsonObject v = new JsonObject(); v.addProperty("type", "object"); v.addProperty("description", description); v.addProperty("additionalProperties", true); return v;
    }
    private static JsonObject stringOrArray(String description) {
        JsonObject v = new JsonObject();
        JsonArray oneOf = new JsonArray();
        oneOf.add(str(description));
        oneOf.add(array(str(description)));
        v.add("oneOf", oneOf);
        return v;
    }
}
