package dev.anomz.blockcinema.client.screen;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.browser.VideoLinks;
import dev.anomz.blockcinema.client.jellyfin.JellyfinLogin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The TV's menu: one tile per service and a power button, nothing else (no address bar). */
public final class TvScreen extends Screen {
    private static final int TILE_WIDTH = 120;
    private static final int TILE_HEIGHT = 40;
    private static final int GAP = 8;
    private static final int COLUMNS = 2;

    private final BlockPos screen;
    private Component status = Component.empty();
    private int gridTop;
    private int gridBottom;

    /** {@code screen} is the origin of the clicked TV's screen (see {@code TvLayout}). */
    public TvScreen(BlockPos screen) {
        super(Component.translatable("screen." + BlockCinema.MODID + ".tv"));
        this.screen = screen;
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
        if (TvPlayers.isPlaying(screen)) {
            addControls(left, gridW);
        }
    }

    /** Below the tiles while something plays: play/pause, fullscreen, the TV's volume and turning it off. */
    private void addControls(int left, int gridW) {
        int half = (gridW - GAP) / 2;
        int y = gridBottom + GAP;
        addRenderableWidget(Button.builder(Component.translatable(key("play_pause")), button -> TvPlayers.togglePause(screen))
                .bounds(left, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(key("fullscreen")),
                button -> minecraft.setScreen(new TvFullscreenScreen(this, screen)))
                .bounds(left + gridW - half, y, half, 20).build());
        y += 20 + 4;
        addRenderableWidget(new VolumeSlider(left, y, gridW, TvPlayers.volume(screen)));
        y += 20 + 4;
        addRenderableWidget(Button.builder(Component.translatable(key("turn_off")), button -> {
            TvPlayers.stop(screen);
            onClose();
        }).bounds(left, y, gridW, 20).build());
        gridBottom = y + 20;
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + "." + name;
    }

    private final class VolumeSlider extends AbstractSliderButton {
        VolumeSlider(int x, int y, int width, double value) {
            super(x, y, width, 20, Component.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key("volume"), Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            TvPlayers.setVolume(screen, (float) value);
        }
    }

    private void open(TvService service) {
        if (!TvPlayers.ready()) {
            status = Component.translatable("screen." + BlockCinema.MODID + ".browser_not_ready");
            return;
        }
        switch (service) {
            case YOUTUBE -> minecraft.setScreen(new LinkScreen(this, screen, service, VideoLinks::youtube));
            case TWITCH -> minecraft.setScreen(new LinkScreen(this, screen, service, VideoLinks::twitch));
            case JELLYFIN -> {
                JellyfinLogin login = JellyfinLogin.load();
                minecraft.setScreen(login != null
                        ? new JellyfinBrowseScreen(this, screen, login, null, service.displayName())
                        : new JellyfinLoginScreen(this, screen));
            }
            case DIRECT_LINK -> minecraft.setScreen(new LinkScreen(this, screen, service, VideoLinks::direct));
        }
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
