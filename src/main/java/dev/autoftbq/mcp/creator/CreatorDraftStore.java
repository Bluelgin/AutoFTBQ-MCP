package dev.autoftbq.mcp.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Short-lived, memory-only preview plans. Does not persist user content,
 * authorize writes, or replace the server revision and permission checks.
 */
public final class CreatorDraftStore {
    public static final int TTL_SECONDS = 600;
    private static final int MAX_DRAFTS = 16;
    private final Map<String, Stored> drafts = new LinkedHashMap<>();

    public record Stored(String id, long expiresAtMillis, CreatorBlueprintCompiler.Compiled plan) {}

    public synchronized Stored remember(CreatorBlueprintCompiler.Compiled plan) {
        purge();
        while (drafts.size() >= MAX_DRAFTS) {
            Iterator<String> iterator = drafts.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
        String id = UUID.randomUUID().toString();
        Stored saved = new Stored(id, System.currentTimeMillis() + TTL_SECONDS * 1000L, clonePlan(plan));
        drafts.put(id, saved);
        return saved;
    }

    public synchronized CreatorBlueprintCompiler.Compiled resolve(String id) {
        purge();
        Stored stored = drafts.get(id);
        return stored == null ? null : clonePlan(stored.plan());
    }

    private void purge() {
        long now = System.currentTimeMillis();
        drafts.values().removeIf(draft -> draft.expiresAtMillis() <= now);
    }

    private static CreatorBlueprintCompiler.Compiled clonePlan(CreatorBlueprintCompiler.Compiled source) {
        JsonObject preview = source.preview().deepCopy();
        JsonArray operations = source.operations().deepCopy();
        return new CreatorBlueprintCompiler.Compiled(preview, operations, List.copyOf(source.itemIds()));
    }
}
