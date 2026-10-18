package dev.anomz.blockcinema.client.screen.ui;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A Block Cinema screen: one panel in the middle with a header (title, an optional second line, back
 * and close buttons). Subclasses set the panel size and add their content below the header.
 */
public abstract class PanelScreen extends Screen {
    @Nullable
    protected final Screen parent;
    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;
    /** Grey line under the title. */
    protected Component subtitle = Component.empty();

    protected PanelScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract int panelWidth();

    protected abstract int panelHeight();

    /** Adds the widgets below the header; {@link #contentTop()} is where they start. */
    protected abstract void initContent();

    /** Whether the header has a back arrow (to {@link #parent}). */
    protected boolean hasBack() {
        return parent != null;
    }

    @Override
    protected final void init() {
        panelW = Math.min(panelWidth(), width - 16);
        panelH = Math.min(panelHeight(), height - 16);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int iconSize = 18;
        int iconY = panelY + (Ui.HEADER - iconSize) / 2;
        addRenderableWidget(new IconButton(panelX + panelW - Ui.PAD - iconSize + 4, iconY, iconSize, Icon.CLOSE,
                Component.translatable("screen." + BlockCinema.MODID + ".close"), IconButton.Style.GHOST, () -> minecraft.setScreen(null)));
        if (hasBack()) {
            addRenderableWidget(new IconButton(panelX + Ui.PAD - 4, iconY, iconSize, Icon.ARROW_BACK,
                    Component.translatable("gui.back"), IconButton.Style.GHOST, this::onClose));
        }
        initContent();
    }

    protected int contentTop() {
        return panelY + Ui.HEADER + Ui.PAD;
    }

    protected int contentLeft() {
        return panelX + Ui.PAD + 2;
    }

    protected int contentWidth() {
        return panelW - 2 * (Ui.PAD + 2);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.panel(g, panelX, panelY, panelW, panelH);
        g.fill(panelX + 1, panelY + Ui.HEADER, panelX + panelW - 1, panelY + Ui.HEADER + 1, Ui.DIVIDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int titleX = panelX + Ui.PAD + (hasBack() ? 20 : 2);
        boolean twoLines = !subtitle.getString().isEmpty();
        int titleY = panelY + (twoLines ? 8 : (Ui.HEADER - font.lineHeight) / 2 + 1);
        int maxW = panelW - (titleX - panelX) - 34;
        g.drawString(font, font.plainSubstrByWidth(title.getString(), maxW), titleX, titleY, Ui.TEXT, false);
        if (twoLines) {
            g.drawString(font, font.plainSubstrByWidth(subtitle.getString(), maxW), titleX, titleY + font.lineHeight + 3, Ui.TEXT_DIM, false);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
