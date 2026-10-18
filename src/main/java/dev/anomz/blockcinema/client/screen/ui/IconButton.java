package dev.anomz.blockcinema.client.screen.ui;

import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A square button showing just an icon; its name shows as a tooltip and is read by the narrator. */
public class IconButton extends AbstractButton {
    public enum Style {
        /** Only shows a background when hovered. */
        GHOST,
        /** Filled with the accent color, for the main action (play/pause). */
        PRIMARY,
        /** Like ghost, turning red when hovered (turn off). */
        DANGER
    }

    private final Supplier<Icon> icon;
    private final Style style;
    private final Runnable action;

    public IconButton(int x, int y, int size, Supplier<Icon> icon, Component name, Style style, Runnable action) {
        super(x, y, size, size, name);
        this.icon = icon;
        this.style = style;
        this.action = action;
        setTooltip(Tooltip.create(name));
    }

    public IconButton(int x, int y, int size, Icon icon, Component name, Style style, Runnable action) {
        this(x, y, size, () -> icon, name, style, action);
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = active && isHoveredOrFocused();
        int iconColor = active ? Ui.TEXT : Ui.TEXT_FAINT;
        switch (style) {
            case PRIMARY -> {
                Ui.round(g, getX(), getY(), width, height, !active ? Ui.SURFACE : hot ? Ui.ACCENT_HOVER : Ui.ACCENT);
                iconColor = active ? 0xFFFFFFFF : Ui.TEXT_FAINT;
            }
            case GHOST, DANGER -> {
                if (hot) {
                    Ui.round(g, getX(), getY(), width, height, Ui.SURFACE_HOVER);
                }
                if (hot && style == Style.DANGER) {
                    iconColor = Ui.DANGER;
                }
            }
        }
        int size = Math.max(8, width - 8);
        Ui.icon(g, icon.get(), getX() + (width - size) / 2, getY() + (height - size) / 2, size, iconColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
