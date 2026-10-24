package dev.autoftbq.mcp.compat.ftbq.v2001.mixin;

import dev.autoftbq.mcp.client.McpContextSelection;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestButton;
import dev.ftb.mods.ftbquests.client.gui.quests.QuestPanel;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestPanel.class, remap = false)
public abstract class QuestPanelMcpContextMixin {
    @Inject(method = "draw", at = @At("TAIL"))
    private void autoftbq$drawMcpSelection(GuiGraphics graphics, Theme theme,
                                            int x, int y, int width, int height,
                                            CallbackInfo callback) {
        QuestPanel panel = (QuestPanel) (Object) this;
        for (Widget widget : panel.getWidgets()) {
            if (!(widget instanceof QuestButton button)) continue;
            String id = ((QuestButtonAccessor) button).autoftbq$getQuest().getCodeString();
            if (!McpContextSelection.containsQuest(id)) continue;
            int left = button.getX() - 2;
            int top = button.getY() - 2;
            int outlineWidth = button.getWidth() + 4;
            int outlineHeight = button.getHeight() + 4;
            graphics.renderOutline(left, top, outlineWidth, outlineHeight, 0xFF55E6B1);
            graphics.renderOutline(left - 1, top - 1, outlineWidth + 2, outlineHeight + 2, 0x9955E6B1);
        }
    }
}
