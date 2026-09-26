package dev.autoftbq.mcp.forge;

import dev.autoftbq.mcp.platform.ModEnvironment;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class ForgeModEnvironment implements ModEnvironment {
    @Override
    public String loaderId() {
        return "forge";
    }

    @Override
    public String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unavailable");
    }

    @Override
    public Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
