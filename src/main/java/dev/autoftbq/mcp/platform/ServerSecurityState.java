package dev.autoftbq.mcp.platform;

public record ServerSecurityState(boolean canEdit, String bookRevision) {
    public static final ServerSecurityState UNAVAILABLE =
            new ServerSecurityState(false, "unavailable");
}
