package dev.autoftbq.mcp.mcp;

import com.google.gson.JsonObject;

import java.util.Set;

public record ToolDefinition(String name, String description, JsonObject inputSchema) {
    private static final Set<String> DESTRUCTIVE = Set.of(
            "ftbq.remove_quest_object",
            "ftbq.delete_quest",
            "ftbq.delete_chapter",
            "ftbq.delete_chapter_group",
            "ftbq.delete_reward_table",
            "ftbq.apply_operations",
            "ftbq.undo_last",
            "ftbq.transaction_commit"
    );

    public JsonObject toJson() {
        JsonObject value = new JsonObject();
        value.addProperty("name", name);
        value.addProperty("description", description);
        value.add("inputSchema", inputSchema);

        JsonObject annotations = new JsonObject();
        boolean readOnly = isReadOnly();
        annotations.addProperty("readOnlyHint", readOnly);
        annotations.addProperty("destructiveHint", !readOnly && DESTRUCTIVE.contains(name));
        annotations.addProperty("idempotentHint", readOnly);
        // AutoFTBQ tools are bounded to the user's local game / connected game server.
        // They do not browse the public internet or arbitrary external entities.
        annotations.addProperty("openWorldHint", false);
        value.add("annotations", annotations);
        return value;
    }

    private boolean isReadOnly() {
        // Capturing a different chapter and cancellation alter the player's
        // ephemeral FTBQ view, so those tools are not truly read-only even
        // though they never change the quest book.
        if (name.equals("ftbq.capture_chapter") || name.equals("ftbq.cancel_capture")) return false;
        return name.equals("autoftbq.health")
                || name.startsWith("minecraft.")
                || name.startsWith("ftbq.get_")
                || name.startsWith("ftbq.list_")
                || name.equals("ftbq.validate_book")
                || name.equals("ftbq.extract_design_profile")
                || name.equals("ftbq.compare_design_profiles")
                || name.equals("ftbq.analyze_layout")
                || name.equals("ftbq.capture_view")
                || name.equals("ftbq.render_chapter_map")
                || name.equals("ftbq.transaction_status");
    }
}
