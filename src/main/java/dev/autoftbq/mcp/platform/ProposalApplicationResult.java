package dev.autoftbq.mcp.platform;

public record ProposalApplicationResult(boolean success, String status, String message,
                                        String bookRevision, String idMapJson) {
}
