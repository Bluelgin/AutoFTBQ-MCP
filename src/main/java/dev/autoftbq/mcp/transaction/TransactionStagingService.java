package dev.autoftbq.mcp.transaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.autoftbq.mcp.client.ClientThread;
import dev.autoftbq.mcp.platform.GamePlatform;
import dev.autoftbq.mcp.platform.ProposalApplicationResult;
import dev.autoftbq.mcp.platform.ServerSecurityState;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class TransactionStagingService {
    private static final int MAX_OPERATIONS = 1000;
    private static final long IDLE_TTL_MILLIS = 15L * 60L * 1000L;

    private final Map<String, Staged> staged = new ConcurrentHashMap<>();

    public JsonObject begin() {
        purgeExpired();
        try {
            ServerSecurityState security = awaitServer(
                    () -> GamePlatform.serverGateway().probeServer(), 8);
            JsonObject result = new JsonObject();
            if (!security.canEdit()) {
                result.addProperty("status", "permission_denied");
                result.addProperty("book_revision", security.bookRevision());
                return result;
            }

            String id = UUID.randomUUID().toString();
            staged.put(id, new Staged(id, security.bookRevision()));
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

        synchronized (current) {
            if (current.committing) {
                return error("conflict", new IllegalStateException("transaction commit is already in progress"));
            }
            if (operations == null || operations.isEmpty()) {
                return error("invalid_argument", new IllegalArgumentException("operations must not be empty"));
            }
            if (current.operations.size() + operations.size() > MAX_OPERATIONS) {
                return error("too_large", new IllegalArgumentException("transaction exceeds 1000 operations"));
            }
            operations.forEach(element -> current.operations.add(element.deepCopy()));
            current.touch();
            return snapshot(current, "staged");
        }
    }

    public JsonObject commit(String id) {
        purgeExpired();
        Staged current = staged.get(id);
        if (current == null) return missing(id);

        final JsonArray operations;
        final String baseRevision;
        synchronized (current) {
            if (current.committing) {
                return error("conflict", new IllegalStateException("transaction commit is already in progress"));
            }
            if (current.operations.isEmpty()) {
                return error("invalid_argument", new IllegalArgumentException("transaction has no operations"));
            }
            current.committing = true;
            current.touch();
            operations = current.operations.deepCopy();
            baseRevision = current.baseRevision;
        }

        try {
            ProposalApplicationResult applied = awaitServer(
                    () -> GamePlatform.serverGateway()
                            .applyProposal(id, baseRevision, operations.toString()),
                    25
            );

            JsonObject result = application(applied);
            result.addProperty("transaction_id", id);
            result.addProperty("operation_count", operations.size());

            boolean terminal = applied.success()
                    || "conflict".equals(applied.status())
                    || "failed".equals(applied.status());

            if (terminal) {
                staged.remove(id, current);
            } else {
                synchronized (current) {
                    current.committing = false;
                    current.touch();
                }
            }
            return result;
        } catch (Exception error) {
            synchronized (current) {
                current.committing = false;
                current.touch();
            }
            return error("unavailable", error);
        }
    }

    public JsonObject abort(String id) {
        purgeExpired();
        Staged current = staged.get(id);
        if (current == null) return missing(id);

        synchronized (current) {
            if (current.committing) {
                return error("conflict", new IllegalStateException("transaction commit is already in progress"));
            }
            boolean removed = staged.remove(id, current);
            JsonObject result = new JsonObject();
            result.addProperty("transaction_id", id);
            result.addProperty("status", removed ? "aborted" : "not_found");
            return result;
        }
    }

    public JsonObject status(String id) {
        purgeExpired();
        Staged current = staged.get(id);
        if (current == null) return missing(id);

        synchronized (current) {
            current.touch();
            return snapshot(current, current.committing ? "committing" : "open");
        }
    }

    private static JsonObject snapshot(Staged current, String status) {
        JsonObject result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("transaction_id", current.id);
        result.addProperty("base_revision", current.baseRevision);
        result.addProperty("operation_count", current.operations.size());
        result.addProperty("idle_ttl_seconds", IDLE_TTL_MILLIS / 1000L);
        result.add("operations", current.operations.deepCopy());
        return result;
    }

    private void purgeExpired() {
        long cutoff = Instant.now().toEpochMilli() - IDLE_TTL_MILLIS;
        staged.forEach((id, value) -> {
            synchronized (value) {
                if (!value.committing && value.lastTouched < cutoff) {
                    staged.remove(id, value);
                }
            }
        });
    }

    private static <T> T awaitServer(
            Supplier<CompletableFuture<T>> supplier, int timeoutSeconds) throws Exception {
        CompletableFuture<T> future = ClientThread.call(supplier);
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
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
        result.addProperty("message",
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        return result;
    }

    private static final class Staged {
        private final String id;
        private final String baseRevision;
        private final JsonArray operations = new JsonArray();
        private long lastTouched = Instant.now().toEpochMilli();
        private boolean committing;

        private Staged(String id, String baseRevision) {
            this.id = id;
            this.baseRevision = baseRevision;
        }

        private void touch() {
            lastTouched = Instant.now().toEpochMilli();
        }
    }
}
