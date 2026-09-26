package dev.autoftbq.mcp;

import dev.autoftbq.mcp.forge.network.ForgeGameServerGateway;
import dev.autoftbq.mcp.forge.network.ForgeMcpNetwork;
import dev.autoftbq.mcp.platform.GamePlatform;
import net.minecraftforge.fml.common.Mod;

@Mod(AutoFTBQMcpMod.MOD_ID)
public final class AutoFTBQMcpMod {
    public static final String MOD_ID = "autoftbq_mcp";
    public static final String VERSION = "0.1.0-alpha.1";

    public AutoFTBQMcpMod() {
        ForgeMcpNetwork.register();
        GamePlatform.installServerGateway(new ForgeGameServerGateway());
    }
}
