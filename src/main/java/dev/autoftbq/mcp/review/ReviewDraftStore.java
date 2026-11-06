package dev.autoftbq.mcp.review;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded, memory-only review plans; storing a draft never grants write permission. */
public final class ReviewDraftStore {
    public static final int TTL_SECONDS = 600;
    private static final int MAX_DRAFTS = 16;

    private record Stored(OperationReviewPlanner.Plan plan, long expiresAtMillis) {}
    public record Ticket(String id, int ttlSeconds) {}

    private final Map<String, Stored> entries = new LinkedHashMap<>();

    public synchronized Ticket store(OperationReviewPlanner.Plan plan) {
        purge();
        while (entries.size() >= MAX_DRAFTS) {
            Iterator<String> iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        String id = UUID.randomUUID().toString();
        entries.put(id, new Stored(copy(plan), System.currentTimeMillis() + TTL_SECONDS * 1000L));
        return new Ticket(id, TTL_SECONDS);
    }

    public synchronized OperationReviewPlanner.Plan resolve(String id) {
        purge();
        Stored result = entries.get(id);
        return result == null ? null : copy(result.plan());
    }

    private void purge() {
        long now = System.currentTimeMillis();
        entries.values().removeIf(value -> value.expiresAtMillis() <= now);
    }

    private static OperationReviewPlanner.Plan copy(OperationReviewPlanner.Plan source) {
        return new OperationReviewPlanner.Plan(
                source.preview().deepCopy(), source.operations().deepCopy(), source.expectedRevision());
    }
}
