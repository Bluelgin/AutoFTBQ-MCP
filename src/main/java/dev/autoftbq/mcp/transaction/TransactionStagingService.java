package dev.autoftbq.mcp.transaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.platform.GamePlatform;
import dev.autoftbq.mcp.platform.ProposalApplicationResult;
import dev.autoftbq.mcp.platform.ServerSecurityState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class TransactionStagingService {
    private static final int MAX_OPERATIONS = 1000;
    private final Map<String, Staged> staged = new ConcurrentHashMap<>();

    public JsonObject begin() {
        purgeExpired();
        try {
            ServerSecurityState security = GamePlatform.serverGateway().probeServer().get(8, TimeUnit.SECONDS);
            JsonObject result = new JsonObject();
            if (!security.canEdit()) {
                result.addProperty("status", "permission_denied");
                result.addProperty("book_revision", security.bookRevision());
                return result;
            }
            String id = UUID.randomUUID().toString();
            staged.put(id, new Staged(id, security.bookRevision(), new JsonArray(), Instant.now().toEpochMilli()));
            result.addProperty("status", "ok");
            result.addProperty("transaction_id", id);
            result.addProperty("base_revision", security.bookRevision());
            return result;
        } catch (Exception error) {
            return error("unavailable", error);
        }
    }

    public JsonObject stage(String id, JsonArray operations) {
        purgeExpired();
        Staged current = staged.get(id);
        if (current == null) return missing(id);
        if (operations == null || operations.isEmpty()) return error("invalid_argument", new IllegalArgumentException("operations must not be empty"));
        if (current.operations.size() + operations.size() > MAX_OPERATIONS) {
            return error("too_large", new IllegalArgumentException("transaction exceeds 1000 operations"));
        }
        operations.forEach(element -> current.operations.add(element.deepCopy()));
        JsonObject result = status(id);
        result.addProperty("status", "staged");
        return result;
    }

    public JsonObject commit(String id) {
        purgeExpired();
        Staged current = staged.get(id);
        if (current == null) return missing(id);
        if (current.operations.isEmpty()) return error("invalid_argument", new IllegalArgumentException("transaction has no operations"));
        try {
            ProposalApplicationResult applied = GamePlatform.serverGateway()
                    .applyProposal(id, current.baseRevision, current.operations.toString())
                    .get(25, TimeUnit.SECONDS);
            JsonObject result = application(applied);
            result.addProperty("transaction_id", id);
            result.addProperty("operation_count", current.operations.size());
            if (applied.success() || "conflict".equals(applied.status()) || "failed".equals(applied.status())) {
                staged.remove(id);
            }
            return result;
        } catch (Exception error) {
            return error("unavailable", error);
        }
    }

    public JsonObject abort(String id) {
        Staged removed = staged.remove(id);
        JsonObject result = new JsonObject();
        result.addProperty("transaction_id", id);
        result.addProperty("status", removed == null ? "not_found" : "aborted");
        return result;
    }

    public JsonObject status(String id) {
        purgeExpired();
        Staged current = staged.get(id);
        if (current == null) return missing(id);
        JsonObject result = new JsonObject();
        result.addProperty("status", "open");
        result.addProperty("transaction_id", current.id);
        result.addProperty("base_revision", current.baseRevision);
        result.addProperty("operation_count", current.operations.size());
        result.add("operations", current.operations.deepCopy());
        return result;
    }

    private void purgeExpired() {
        long cutoff = Instant.now().minusSeconds(15 * 60).toEpochMilli();
        staged.values().removeIf(value -> value.createdAt < cutoff);
    }

    public static JsonObject application(ProposalApplicationResult value) {
        JsonObject result = new JsonObject();
        result.addProperty("success", value.success());
        result.addProperty("status", value.status());
        result.addProperty("message", value.message());
        result.addProperty("book_revision", value.bookRevision());
        try {
            result.add("temporary_ids", com.google.gson.JsonParser.parseString(value.idMapJson()));
        } catch (RuntimeException ignored) {
            result.add("temporary_ids", new JsonObject());
        }
        return result;
    }

    private static JsonObject missing(String id) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "not_found");
        result.addProperty("transaction_id", id);
        return result;
    }

    private static JsonObject error(String status, Exception error) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        return result;
    }

    private static final class Staged {
        private final String id;
        private final String baseRevision;
        private final JsonArray operations;
        private final long createdAt;

        private Staged(String id, String baseRevision, JsonArray operations, long createdAt) {
            this.id = id;
            this.baseRevision = baseRevision;
            this.operations = operations;
            this.createdAt = createdAt;
        }
    }
}
