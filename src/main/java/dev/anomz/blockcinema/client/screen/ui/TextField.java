package dev.anomz.blockcinema.client.screen.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** A text field in a rounded box; the box lights up with the accent while typing. */
public class TextField extends EditBox {
    private static final int INSET_X = 7;

    /** {@code x, y, width, height}: the whole box, 20 high is usual. */
    public TextField(int x, int y, int width, int height, Component name) {
        super(Minecraft.getInstance().font, x + INSET_X, y + (height - 8) / 2, width - 2 * INSET_X, 8, name);
        setBordered(false);
        setTextColor(Ui.TEXT);
        setHint(name.copy().withColor(Ui.TEXT_FAINT));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int boxY = getY() - 6;
        Ui.box(g, getX() - INSET_X, boxY, width + 2 * INSET_X, height + 12, Ui.SURFACE, isFocused() ? Ui.ACCENT : Ui.PANEL_BORDER);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }

    /** The whole box takes clicks, not just the line of text. */
    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= getX() - INSET_X && mouseX < getX() + width + INSET_X
                && mouseY >= getY() - 6 && mouseY < getY() + height + 6;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY) && !super.isMouseOver(mouseX, mouseY)) {
            // Clicked the box around the text: focus, with the cursor where it is.
            setFocused(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
