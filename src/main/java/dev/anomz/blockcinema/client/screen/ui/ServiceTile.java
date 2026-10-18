package dev.anomz.blockcinema.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A card for one service: icon, name and a short line saying what it plays. */
public class ServiceTile extends AbstractButton {
    private final Icon icon;
    private final Component detail;
    private final Runnable action;

    public ServiceTile(int x, int y, int width, int height, Icon icon, Component name, Component detail, Runnable action) {
        super(x, y, width, height, name);
        this.icon = icon;
        this.detail = detail;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        Ui.box(g, getX(), getY(), width, height, hot ? Ui.SURFACE_HOVER : Ui.SURFACE, hot ? Ui.ACCENT : Ui.DIVIDER);
        int iconSize = 18;
        int iconX = getX() + 10;
        Ui.icon(g, icon, iconX, getY() + (height - iconSize) / 2, iconSize, !active ? Ui.TEXT_FAINT : hot ? Ui.ACCENT_HOVER : Ui.TEXT);
        Font font = Minecraft.getInstance().font;
        int textX = iconX + iconSize + 9;
        int textW = width - (textX - getX()) - 6;
        int top = getY() + height / 2 - font.lineHeight;
        g.drawString(font, font.plainSubstrByWidth(getMessage().getString(), textW), textX, top, active ? Ui.TEXT : Ui.TEXT_FAINT, false);
        g.drawString(font, font.plainSubstrByWidth(detail.getString(), textW), textX, top + font.lineHeight + 2,
                active ? Ui.TEXT_DIM : Ui.TEXT_FAINT, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
