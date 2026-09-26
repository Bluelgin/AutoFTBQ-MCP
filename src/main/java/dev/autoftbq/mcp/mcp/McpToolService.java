package dev.autoftbq.mcp.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.autoftbq.mcp.client.ClientThread;
import dev.autoftbq.mcp.compat.CompatibilityManager;
import dev.autoftbq.mcp.compat.ftbq.FtbqClientAdapter;
import dev.autoftbq.mcp.compat.ftbq.v2001.GameDataCatalog;
import dev.autoftbq.mcp.forge.network.ForgeMcpNetwork;
import dev.autoftbq.mcp.platform.GamePlatform;
import dev.autoftbq.mcp.platform.ProposalApplicationResult;
import dev.autoftbq.mcp.platform.ProposalUndoResult;
import dev.autoftbq.mcp.platform.ServerSecurityState;
import dev.autoftbq.mcp.transaction.TransactionStagingService;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class McpToolService {
    private final TransactionStagingService transactions = new TransactionStagingService();

    public JsonObject invoke(String name, JsonObject args) {
        if (args == null) args = new JsonObject();
        return switch (name) {
            case "autoftbq.health" -> health();
            case "minecraft.capabilities" -> client(() -> GameDataCatalog.query(withKind(new JsonObject(), "capabilities")));
            case "minecraft.search_registry" -> client(() -> GameDataCatalog.query(withKind(args.deepCopy(), "registry_page")));
            case "minecraft.validate_ids" -> client(() -> adapter().query("validate_registry_ids", args));
            case "minecraft.inspect_item" -> client(() -> GameDataCatalog.query(withKind(args.deepCopy(), "item_evidence")));
            case "minecraft.search_recipes" -> client(() -> GameDataCatalog.query(withKind(args.deepCopy(), "recipes")));
            case "minecraft.inspect_resource" -> serverResource(args);

            case "ftbq.get_context" -> client(() -> adapter().context());
            case "ftbq.get_book" -> client(() -> adapter().book());
            case "ftbq.get_chapter" -> client(() -> adapter().chapter(required(args, "id")));
            case "ftbq.get_quest" -> client(() -> adapter().quest(required(args, "id")));
            case "ftbq.get_object" -> client(() -> adapter().object(required(args, "id")));
            case "ftbq.list_task_types" -> client(() -> adapter().listTypes("task"));
            case "ftbq.list_reward_types" -> client(() -> adapter().listTypes("reward"));
            case "ftbq.get_type_schema" -> client(() -> adapter().typeSchema(required(args, "kind"), required(args, "type_id")));

            case "ftbq.create_chapter" -> commitSingle(args, createChapter(args));
            case "ftbq.create_quest" -> commitSingle(args, createQuest(args));
            case "ftbq.update_quest" -> commitSingle(args, updateQuest(args));
            case "ftbq.add_task" -> commitSingle(args, addTask(args));
            case "ftbq.add_reward" -> commitSingle(args, addReward(args));
            case "ftbq.remove_quest_object" -> commitSingle(args, op("remove_quest_object", "object_id", required(args, "object_id")));
            case "ftbq.delete_quest" -> commitSingle(args, op("delete_quest", "quest_id", required(args, "quest_id")));
            case "ftbq.delete_chapter" -> commitSingle(args, op("delete_chapter", "chapter_id", required(args, "chapter_id")));
            case "ftbq.connect_quests" -> commitSingle(args, dependency(args));
            case "ftbq.apply_dependency_plan" -> commit(args, dependencyPlan(args));
            case "ftbq.apply_operations" -> commit(args, requiredArray(args, "operations"));
            case "ftbq.undo_last" -> undo(args);

            case "ftbq.transaction_begin" -> transactions.begin();
            case "ftbq.transaction_stage" -> transactions.stage(required(args, "transaction_id"), requiredArray(args, "operations"));
            case "ftbq.transaction_status" -> transactions.status(required(args, "transaction_id"));
            case "ftbq.transaction_commit" -> transactions.commit(required(args, "transaction_id"));
            case "ftbq.transaction_abort" -> transactions.abort(required(args, "transaction_id"));
            default -> error("unknown_tool", "Unknown tool: " + name);
        };
    }

    private JsonObject health() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.add("compatibility", CompatibilityManager.descriptor());
        result.add("adapter", client(() -> adapter().capabilities()));
        try {
            ServerSecurityState security = awaitServer(() -> GamePlatform.serverGateway().probeServer(), 8);
            JsonObject server = new JsonObject();
            server.addProperty("can_edit", security.canEdit());
            server.addProperty("book_revision", security.bookRevision());
            server.addProperty("status", "ok");
            result.add("server", server);
        } catch (Exception error) {
            JsonObject server = new JsonObject();
            server.addProperty("status", "unavailable");
            server.addProperty("message", safe(error));
            result.add("server", server);
        }
        return result;
    }

    private JsonObject serverResource(JsonObject args) {
        try {
            String raw = awaitServer(() -> ForgeMcpNetwork.queryData(args.toString()), 10);
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception error) {
            return error("unavailable", safe(error));
        }
    }

    private JsonObject commitSingle(JsonObject request, JsonObject operation) {
        JsonArray array = new JsonArray();
        array.add(operation);
        return commit(request, array);
    }

    private JsonObject commit(JsonObject request, JsonArray operations) {
        if (operations.isEmpty()) return error("invalid_argument", "operations must not be empty");
        String expectedRevision = required(request, "expected_revision");
        String proposalId = optional(request, "proposal_id");
        if (proposalId.isBlank()) proposalId = UUID.randomUUID().toString();
        try {
            final String pid = proposalId;
            ProposalApplicationResult applied = awaitServer(
                    () -> GamePlatform.serverGateway().applyProposal(pid, expectedRevision, operations.toString()), 30);
            JsonObject result = TransactionStagingService.application(applied);
            result.addProperty("proposal_id", proposalId);
            result.addProperty("operation_count", operations.size());
            return result;
        } catch (Exception error) {
            return error("unavailable", safe(error));
        }
    }

    private JsonObject undo(JsonObject args) {
        String proposalId = required(args, "proposal_id");
        String expectedRevision = required(args, "expected_revision");
        try {
            ProposalUndoResult undone = awaitServer(
                    () -> GamePlatform.serverGateway().undoProposal(proposalId, expectedRevision), 25);
            JsonObject result = new JsonObject();
            result.addProperty("success", undone.success());
            result.addProperty("status", undone.status());
            result.addProperty("message", undone.message());
            result.addProperty("book_revision", undone.bookRevision());
            result.addProperty("proposal_id", proposalId);
            return result;
        } catch (Exception error) {
            return error("unavailable", safe(error));
        }
    }

    private static JsonObject createChapter(JsonObject args) {
        JsonObject op = new JsonObject();
        op.addProperty("kind", "create_chapter");
        op.addProperty("temp_id", temp(args));
        op.addProperty("title", required(args, "title"));
        copyString(args, op, "subtitle");
        copyString(args, op, "icon");
        return op;
    }

    private static JsonObject createQuest(JsonObject args) {
        JsonObject op = new JsonObject();
        op.addProperty("kind", "create_quest");
        op.addProperty("temp_id", temp(args));
        op.addProperty("chapter_id", required(args, "chapter_id"));
        op.addProperty("title", required(args, "title"));
        op.addProperty("x", requiredNumber(args, "x"));
        op.addProperty("y", requiredNumber(args, "y"));
        copyString(args, op, "subtitle");
        copyString(args, op, "icon");
        if (args.has("description")) op.add("description", args.get("description").deepCopy());
        return op;
    }

    private static JsonObject updateQuest(JsonObject args) {
        JsonObject op = new JsonObject();
        op.addProperty("kind", "update_quest");
        op.addProperty("quest_id", required(args, "quest_id"));
        JsonObject changes = requiredObject(args, "changes");
        op.add("changes", changes.deepCopy());
        return op;
    }

    private static JsonObject addTask(JsonObject args) {
        String type = normalizeType(required(args, "type_id"));
        JsonObject op = new JsonObject();
        if ("item".equals(type) && args.has("item_id")) {
            op.addProperty("kind", "add_item_task");
            op.addProperty("quest_id", required(args, "quest_id"));
            op.addProperty("item_id", required(args, "item_id"));
            op.addProperty("count", args.has("count") ? args.get("count").getAsInt() : 1);
            return op;
        }
        if ("checkmark".equals(type)) {
            op.addProperty("kind", "add_checkmark_task");
            op.addProperty("quest_id", required(args, "quest_id"));
            return op;
        }
        if ("xp".equals(type) && args.has("amount")) {
            op.addProperty("kind", "add_xp_task");
            op.addProperty("quest_id", required(args, "quest_id"));
            op.addProperty("amount", args.get("amount").getAsLong());
            return op;
        }
        op.addProperty("kind", "add_typed_quest_object");
        op.addProperty("quest_id", required(args, "quest_id"));
        op.addProperty("object_kind", "task");
        op.addProperty("type_id", required(args, "type_id"));
        op.addProperty("data_snbt", args.has("data_snbt") ? args.get("data_snbt").getAsString() : "{}");
        return op;
    }

    private static JsonObject addReward(JsonObject args) {
        String type = normalizeType(required(args, "type_id"));
        JsonObject op = new JsonObject();
        if ("item".equals(type) && args.has("item_id")) {
            op.addProperty("kind", "add_item_reward");
            op.addProperty("quest_id", required(args, "quest_id"));
            op.addProperty("item_id", required(args, "item_id"));
            op.addProperty("count", args.has("count") ? args.get("count").getAsInt() : 1);
            return op;
        }
        if (("xp".equals(type) || "xp_levels".equals(type)) && args.has("amount")) {
            op.addProperty("kind", "xp_levels".equals(type) ? "add_xp_levels_reward" : "add_xp_reward");
            op.addProperty("quest_id", required(args, "quest_id"));
            op.addProperty("amount", args.get("amount").getAsInt());
            return op;
        }
        op.addProperty("kind", "add_typed_quest_object");
        op.addProperty("quest_id", required(args, "quest_id"));
        op.addProperty("object_kind", "reward");
        op.addProperty("type_id", required(args, "type_id"));
        op.addProperty("data_snbt", args.has("data_snbt") ? args.get("data_snbt").getAsString() : "{}");
        return op;
    }

    private static JsonObject dependency(JsonObject args) {
        JsonObject op = new JsonObject();
        op.addProperty("kind", "add_dependency");
        op.addProperty("quest_id", required(args, "quest_id"));
        op.addProperty("dependency_id", required(args, "dependency_id"));
        return op;
    }

    private static JsonArray dependencyPlan(JsonObject args) {
        JsonArray edges = requiredArray(args, "edges");
        if (edges.size() > 40) throw new IllegalArgumentException("edges must contain at most 40 entries");
        JsonArray operations = new JsonArray();
        for (JsonElement element : edges) {
            JsonObject edge = element.getAsJsonObject();
            operations.add(dependency(edge));
        }
        return operations;
    }

    private static JsonObject op(String kind, String key, String value) {
        JsonObject op = new JsonObject();
        op.addProperty("kind", kind);
        op.addProperty(key, value);
        return op;
    }

    private static String normalizeType(String typeId) {
        int colon = typeId.indexOf(':');
        return colon >= 0 && typeId.substring(0, colon).equals("ftbquests") ? typeId.substring(colon + 1) : typeId;
    }

    private static String temp(JsonObject args) {
        String value = optional(args, "temp_id");
        return value.isBlank() ? "temp:" + UUID.randomUUID() : value;
    }

    private static FtbqClientAdapter adapter() {
        return CompatibilityManager.currentFtbqAdapter();
    }

    private static JsonObject client(Supplier<JsonObject> supplier) {
        try { return ClientThread.call(supplier); }
        catch (Exception error) { return error("unavailable", safe(error)); }
    }

    private static <T> T awaitServer(Supplier<CompletableFuture<T>> supplier, int seconds) throws Exception {
        CompletableFuture<T> future = ClientThread.call(supplier);
        return future.get(seconds, TimeUnit.SECONDS);
    }

    private static JsonObject withKind(JsonObject args, String kind) {
        args.addProperty("kind", kind);
        return args;
    }

    private static String required(JsonObject args, String key) {
        if (args == null || !args.has(key) || args.get(key).isJsonNull()) throw new IllegalArgumentException("Missing required argument: " + key);
        String value = args.get(key).getAsString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Argument must not be blank: " + key);
        return value;
    }

    private static String optional(JsonObject args, String key) {
        return args != null && args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString().trim() : "";
    }

    private static JsonArray requiredArray(JsonObject args, String key) {
        if (args == null || !args.has(key) || !args.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        return args.getAsJsonArray(key);
    }

    private static JsonObject requiredObject(JsonObject args, String key) {
        if (args == null || !args.has(key) || !args.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        return args.getAsJsonObject(key);
    }

    private static double requiredNumber(JsonObject args, String key) {
        if (args == null || !args.has(key)) throw new IllegalArgumentException("Missing required argument: " + key);
        double value = args.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(key + " must be finite");
        return value;
    }

    private static void copyString(JsonObject from, JsonObject to, String key) {
        if (from.has(key) && !from.get(key).isJsonNull()) to.addProperty(key, from.get(key).getAsString());
    }

    private static JsonObject error(String status, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("message", message);
        return result;
    }

    private static String safe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
