package dev.autoftbq.mcp.compat.ftbq.v2001;

import dev.autoftbq.mcp.compat.ftbq.FtbqAdapterProvider;
import dev.autoftbq.mcp.compat.ftbq.FtbqClientAdapter;

public final class FTBQ2001AdapterProvider implements FtbqAdapterProvider {
    @Override public String generation() { return "2001"; }

    @Override
    public boolean supports(String ftbqVersion) {
        return ftbqVersion != null && ftbqVersion.startsWith("2001.");
    }

    @Override
    public FtbqClientAdapter adapter() {
        return FTBQ2001Adapter.INSTANCE;
    }
}
