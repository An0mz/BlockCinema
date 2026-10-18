package dev.anomz.blockcinema.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A rounded text button: filled with the accent for the main action, or quiet for the others. */
public class TextButton extends AbstractButton {
    private final boolean primary;
    private final Runnable action;

    public TextButton(int x, int y, int width, int height, Component label, boolean primary, Runnable action) {
        super(x, y, width, height, label);
        this.primary = primary;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        int fill;
        int text;
        if (!active) {
            fill = Ui.SURFACE;
            text = Ui.TEXT_FAINT;
        } else if (primary) {
            fill = hot ? Ui.ACCENT_HOVER : Ui.ACCENT;
            text = 0xFFFFFFFF;
        } else {
            fill = hot ? Ui.SURFACE_HOVER : Ui.SURFACE;
            text = Ui.TEXT;
        }
        Ui.round(g, getX(), getY(), width, height, fill);
        Font font = Minecraft.getInstance().font;
        g.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2,
                getY() + (height - font.lineHeight) / 2 + 1, text, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
