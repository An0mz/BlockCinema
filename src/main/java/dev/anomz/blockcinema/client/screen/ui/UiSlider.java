package dev.anomz.blockcinema.client.screen.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A thin track with an accent fill and a knob shown on hover, instead of the stone slider. The label
 * is only for the narrator; screens draw any numbers beside it.
 */
public abstract class UiSlider extends AbstractSliderButton {
    protected UiSlider(int x, int y, int width, int height, double value) {
        super(x, y, width, height, Component.empty(), value);
    }

    /** Moves the slider without acting on it. */
    public void show(double value) {
        this.value = Math.max(0, Math.min(1, value));
        updateMessage();
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The vanilla slider maps the mouse onto x + 4 .. x + width - 4; the track matches that.
        int left = getX() + 4;
        int right = getX() + width - 4;
        int mid = getY() + height / 2;
        Ui.round(g, left, mid - 2, right - left, 4, Ui.TRACK);
        if (!active) {
            return;
        }
        int at = left + (int) Math.round(value * (right - left));
        if (at - left >= 2) {
            Ui.round(g, left, mid - 2, at - left, 4, Ui.ACCENT);
        }
        if (isHoveredOrFocused()) {
            Ui.round(g, at - 4, mid - 4, 8, 8, 0xFFFFFFFF);
        }
    }
}
