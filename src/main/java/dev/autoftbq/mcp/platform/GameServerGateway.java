package dev.autoftbq.mcp.platform;

import java.util.concurrent.CompletableFuture;

/** Loader-neutral client facade for server-authoritative operations. */
public interface GameServerGateway {
    CompletableFuture<ServerSecurityState> probeServer();

    CompletableFuture<ProposalApplicationResult> applyProposal(
            String proposalId, String expectedRevision, String operationsJson);

    CompletableFuture<ProposalUndoResult> undoProposal(
            String proposalId, String expectedRevision);

    /**
     * Loader-neutral server data query channel for authoritative game facts
     * that do not exist safely on the client (for example structures or
     * datapack resources).
     */
    CompletableFuture<String> queryData(String argumentsJson);
}
