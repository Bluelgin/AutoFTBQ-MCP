package dev.autoftbq.mcp.forge.network;

import dev.autoftbq.mcp.platform.GameServerGateway;
import dev.autoftbq.mcp.platform.ProposalApplicationResult;
import dev.autoftbq.mcp.platform.ProposalUndoResult;
import dev.autoftbq.mcp.platform.ServerSecurityState;

import java.util.concurrent.CompletableFuture;

public final class ForgeGameServerGateway implements GameServerGateway {
    @Override
    public CompletableFuture<ServerSecurityState> probeServer() {
        return ForgeMcpNetwork.probeServer();
    }

    @Override
    public CompletableFuture<ProposalApplicationResult> applyProposal(
            String proposalId, String expectedRevision, String operationsJson) {
        return ForgeMcpNetwork.applyProposal(proposalId, expectedRevision, operationsJson)
                .thenApply(value -> new ProposalApplicationResult(
                        value.success(), value.status(), value.message(),
                        value.bookRevision(), value.idMapJson()));
    }

    @Override
    public CompletableFuture<ProposalUndoResult> undoProposal(
            String proposalId, String expectedRevision) {
        return ForgeMcpNetwork.undoProposal(proposalId, expectedRevision)
                .thenApply(value -> new ProposalUndoResult(
                        value.success(), value.status(), value.message(), value.bookRevision()));
    }
}
