package dev.anomz.blockcinema.client.screen;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The TV's menu: one tile per service, nothing else (no address bar). */
public final class TvScreen extends Screen {
    private static final int TILE_WIDTH = 120;
    private static final int TILE_HEIGHT = 40;
    private static final int GAP = 8;
    private static final int COLUMNS = 2;

    private final BlockPos tv;
    private Component status = Component.empty();
    private int gridTop;
    private int gridBottom;

    /** {@code tv} is the TV that was clicked; the players will use it to know which screen to fill. */
    public TvScreen(BlockPos tv) {
        super(Component.translatable("screen." + BlockCinema.MODID + ".tv"));
        this.tv = tv;
    }

    @Override
    protected void init() {
        TvService[] services = TvService.values();
        int rows = (services.length + COLUMNS - 1) / COLUMNS;
        int gridW = COLUMNS * TILE_WIDTH + (COLUMNS - 1) * GAP;
        int gridH = rows * TILE_HEIGHT + (rows - 1) * GAP;
        int left = (width - gridW) / 2;
        int top = (height - gridH) / 2;
        gridTop = top;
        gridBottom = top + gridH;
        for (int i = 0; i < services.length; i++) {
            TvService service = services[i];
            int x = left + i % COLUMNS * (TILE_WIDTH + GAP);
            int y = top + i / COLUMNS * (TILE_HEIGHT + GAP);
            addRenderableWidget(Button.builder(service.displayName(), button -> open(service))
                    .bounds(x, y, TILE_WIDTH, TILE_HEIGHT).build());
        }
    }

    private void open(TvService service) {
        // The players come in later milestones.
        status = Component.translatable("screen." + BlockCinema.MODID + ".not_available", service.displayName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, gridTop - 20, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, gridBottom + 12, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
