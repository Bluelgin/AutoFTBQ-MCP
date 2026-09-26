package dev.autoftbq.mcp.platform;

public record ProposalUndoResult(boolean success, String status, String message,
                                 String bookRevision) {
}
