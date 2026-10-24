package dev.autoftbq.mcp.compat.ftbq;

public interface FtbqAdapterProvider {
    String generation();
    boolean supports(String ftbqVersion);
    FtbqClientAdapter adapter();
}
