package dev.autoftbq.mcp.forge;

import dev.autoftbq.mcp.AutoFTBQMcpMod;
import dev.autoftbq.mcp.compat.ftbq.v2001.GameDataCatalog;
import dev.autoftbq.mcp.mcp.McpRuntime;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class AutoFTBQMcpClient {
    private AutoFTBQMcpClient() {}

    @Mod.EventBusSubscriber(modid = AutoFTBQMcpMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(McpRuntime::start);
        }
    }

    @Mod.EventBusSubscriber(modid = AutoFTBQMcpMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeEvents {
        @SubscribeEvent public static void recipesChanged(RecipesUpdatedEvent event) { GameDataCatalog.invalidate(); }
        @SubscribeEvent public static void tagsChanged(TagsUpdatedEvent event) { GameDataCatalog.invalidate(); }
    }
}
