package dev.autoftbq.mcp;

import dev.autoftbq.mcp.compat.CompatibilityManager;
import dev.autoftbq.mcp.compat.ftbq.v2001.FTBQ2001AdapterProvider;
import dev.autoftbq.mcp.forge.ForgeModEnvironment;
import dev.autoftbq.mcp.forge.network.ForgeGameServerGateway;
import dev.autoftbq.mcp.forge.network.ForgeMcpNetwork;
import dev.autoftbq.mcp.platform.GamePlatform;
import net.minecraftforge.fml.common.Mod;

@Mod(AutoFTBQMcpMod.MOD_ID)
public final class AutoFTBQMcpMod {
    public static final String MOD_ID = "autoftbq_mcp";
    public static final String VERSION = "0.1.0-alpha.1";

    public AutoFTBQMcpMod() {
        GamePlatform.installEnvironment(new ForgeModEnvironment());
        GamePlatform.installServerGateway(new ForgeGameServerGateway());
        CompatibilityManager.registerFtbqAdapter(new FTBQ2001AdapterProvider());
        ForgeMcpNetwork.register();
    }
}
