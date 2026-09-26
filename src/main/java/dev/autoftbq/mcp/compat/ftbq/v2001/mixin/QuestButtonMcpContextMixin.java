package dev.autoftbq.mcp.compat.ftbq.v2001.mixin;

import dev.autoftbq.mcp.client.McpContextSelection;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestButton.class, remap = false)
public abstract class QuestButtonMcpContextMixin {
    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void autoftbq$toggleMcpQuest(MouseButton button, CallbackInfo callback) {
        if (!button.isLeft() || !Screen.hasAltDown()) return;
        String id = ((QuestButtonAccessor) this).autoftbq$getQuest().getCodeString();
        McpContextSelection.toggleQuest(id);
        callback.cancel();
    }
}
