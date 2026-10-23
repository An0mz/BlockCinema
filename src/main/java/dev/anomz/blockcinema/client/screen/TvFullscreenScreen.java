package dev.anomz.blockcinema.client.screen;

import org.lwjgl.glfw.GLFW;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.screen.ui.Icon;
import dev.anomz.blockcinema.client.screen.ui.IconButton;
import dev.anomz.blockcinema.client.screen.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A TV's picture filling the game window: 16:9 with a control bar below it, or in the vertical view
 * (for Shorts) 9:16 with its buttons beside it. Nothing is drawn over the picture (YouTube's terms forbid
 * covering its player). A click on the picture pauses. In the vertical view the wheel and up/down go to
 * the next or previous video; in the wide view they change the volume. Esc goes back.
 */
public final class TvFullscreenScreen extends Screen {
    private static final float VOLUME_STEP = 0.1f;
    private static final float WHEEL_VOLUME_STEP = 0.05f;
    private static final int SKIP_SECONDS = 10;
    /** The control bar below a wide picture. */
    private static final int BAR_H = 44;
    /** Room on each side of a vertical picture, for its buttons. */
    private static final int SIDE_W = 76;

    private final Screen parent;
    private final BlockPos screen;
    private final SkipGate scrollGate = new SkipGate();
    private boolean vertical;
    private TimeBar timeBar;
    private VolumeSlider volume;
    private float volumeBeforeMute = 1;
    private int picX;
    private int picY;
    private int picW;
    private int picH;

    public TvFullscreenScreen(Screen parent, BlockPos screen) {
        super(Component.translatable(key("fullscreen")));
        this.parent = parent;
        this.screen = screen;
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + "." + name;
    }

    @Override
    protected void init() {
        if (timeBar == null) {
            // Shorts links and screens taller than wide start in the vertical view.
            TvSource source = TvRemote.source(screen);
            vertical = source != null && TvPageRules.youtubeShort(source.page())
                    || minecraft.level != null && minecraft.level.getBlockEntity(screen) instanceof TvBlockEntity tv
                    && tv.layout(minecraft.level.getGameTime()).portrait();
        }
        TvPlayers.setFullscreen(screen, true);
        TvPlayers.setVerticalView(screen, vertical);
        if (vertical) {
            initVertical();
        } else {
            initWide();
        }
    }

    private void initWide() {
        picW = Math.min(width, (height - BAR_H) * 16 / 9);
        picH = picW * 9 / 16;
        picX = (width - picW) / 2;
        picY = (height - BAR_H - picH) / 2;
        int barY = height - BAR_H;
        timeBar = addRenderableWidget(new TimeBar(8, barY + 5, width - 16, screen));
        int rowY = barY + 20;
        int mid = width / 2;
        addRenderableWidget(new IconButton(mid - 12, rowY, 24, () -> paused() ? Icon.PLAY : Icon.PAUSE,
                Component.translatable(key("play_pause")), IconButton.Style.PRIMARY, () -> TvRemote.togglePause(screen)));
        addRenderableWidget(new IconButton(mid - 12 - 6 - 20, rowY + 2, 20, Icon.BACK,
                Component.translatable(key("skip_back"), SKIP_SECONDS), IconButton.Style.GHOST, () -> TvRemote.skip(screen, -SKIP_SECONDS)));
        addRenderableWidget(new IconButton(mid + 12 + 6, rowY + 2, 20, Icon.FORWARD,
                Component.translatable(key("skip_forward"), SKIP_SECONDS), IconButton.Style.GHOST, () -> TvRemote.skip(screen, SKIP_SECONDS)));
        addRenderableWidget(new IconButton(mid - 12 - 6 - 20 - 4 - 20, rowY + 2, 20, Icon.PREVIOUS,
                Component.translatable(key("previous_video")), IconButton.Style.GHOST, () -> skipVideo(-1)));
        addRenderableWidget(new IconButton(mid + 12 + 6 + 20 + 4, rowY + 2, 20, Icon.NEXT,
                Component.translatable(key("next_video")), IconButton.Style.GHOST, () -> skipVideo(1)));
        addRenderableWidget(muteButton(8, rowY + 2));
        volume = addRenderableWidget(new VolumeSlider(30, rowY + 6, 60, screen));
        addRenderableWidget(new IconButton(width - 8 - 20 - 4 - 20, rowY + 2, 20, Icon.PHONE,
                Component.translatable(key("vertical_view")), IconButton.Style.GHOST, this::toggleView));
        addRenderableWidget(exitButton(width - 8 - 20, rowY + 2));
    }

    /** Shorts-style: the picture in the middle, up and down at the top right of it, the rest below them. */
    private void initVertical() {
        picH = Math.min(height - 30, (width - 2 * SIDE_W) * 16 / 9);
        picW = picH * 9 / 16;
        picX = (width - picW) / 2;
        picY = (height - 18 - picH) / 2;
        timeBar = addRenderableWidget(new TimeBar(picX, picY + picH + 4, picW, screen));
        int sideX = picX + picW + 12;
        addRenderableWidget(new IconButton(sideX, picY, 24, Icon.UP,
                Component.translatable(key("previous_video")), IconButton.Style.GHOST, () -> skipVideo(-1)));
        addRenderableWidget(new IconButton(sideX, picY + 28, 24, Icon.DOWN,
                Component.translatable(key("next_video")), IconButton.Style.GHOST, () -> skipVideo(1)));
        int bottom = picY + picH;
        addRenderableWidget(new IconButton(sideX, bottom - 92, 24, () -> paused() ? Icon.PLAY : Icon.PAUSE,
                Component.translatable(key("play_pause")), IconButton.Style.PRIMARY, () -> TvRemote.togglePause(screen)));
        addRenderableWidget(muteButton(sideX, bottom - 64));
        volume = addRenderableWidget(new VolumeSlider(sideX + 22, bottom - 60, 42, screen));
        addRenderableWidget(new IconButton(sideX, bottom - 44, 20, Icon.WIDE,
                Component.translatable(key("wide_view")), IconButton.Style.GHOST, this::toggleView));
        addRenderableWidget(exitButton(sideX, bottom - 20));
    }

    private IconButton muteButton(int x, int y) {
        return new IconButton(x, y, 20, () -> TvPlayers.volume(screen) <= 0 ? Icon.MUTE : Icon.VOLUME,
                Component.translatable(key("mute")), IconButton.Style.GHOST, this::toggleMute);
    }

    private IconButton exitButton(int x, int y) {
        return new IconButton(x, y, 20, Icon.CLOSE, Component.translatable(key("exit_fullscreen")), IconButton.Style.GHOST, this::onClose);
    }

    private boolean paused() {
        TvSource source = TvRemote.source(screen);
        return source != null && source.paused();
    }

    private void skipVideo(int direction) {
        Component problem = TvRemote.skipVideo(screen, direction);
        if (problem != null) {
            timeBar.notice(problem);
        }
    }

    private void toggleView() {
        vertical = !vertical;
        rebuildWidgets();
    }

    private void toggleMute() {
        float now = TvPlayers.volume(screen);
        if (now > 0) {
            volumeBeforeMute = now;
            setVolume(0);
        } else {
            setVolume(volumeBeforeMute);
        }
    }

    private void setVolume(float value) {
        TvPlayers.setVolume(screen, value);
        volume.show(TvPlayers.volume(screen));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        if (!vertical) {
            graphics.fill(0, height - BAR_H, width, height, Ui.SURFACE);
            graphics.fill(0, height - BAR_H, width, height - BAR_H + 1, Ui.DIVIDER);
        }
        ResourceLocation picture = TvPlayers.texture(screen);
        // Until the browser has turned to the view's shape, there is nothing right to show.
        if (picture != null && TvPlayers.portrait(screen) == vertical) {
            TvPlayers.markSeen(screen);
            // The whole texture, whatever size the browser is.
            graphics.blit(picture, picX, picY, picW, picH, 0, 0, 16, 9, 16, 9);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        // Turned off, broken or too far away.
        if (TvRemote.source(screen) == null) {
            onClose();
            return;
        }
        timeBar.refresh();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The picture covers the world; no blur or dimming behind it.
    }

    private boolean onPicture(double mouseX, double mouseY) {
        return mouseX >= picX && mouseX < picX + picW && mouseY >= picY && mouseY < picY + picH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (vertical) {
            // Shorts-style: down for the next video, up for the previous one.
            if (scrollGate.allow()) {
                skipVideo(scrollY < 0 ? 1 : -1);
            }
        } else {
            setVolume(TvPlayers.volume(screen) + (scrollY > 0 ? WHEEL_VOLUME_STEP : -WHEEL_VOLUME_STEP));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && onPicture(mouseX, mouseY)) {
            TvRemote.togglePause(screen);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> TvRemote.togglePause(screen);
            case GLFW.GLFW_KEY_LEFT -> TvRemote.skip(screen, -SKIP_SECONDS);
            case GLFW.GLFW_KEY_RIGHT -> TvRemote.skip(screen, SKIP_SECONDS);
            case GLFW.GLFW_KEY_UP -> {
                if (vertical) {
                    skipVideo(-1);
                } else {
                    setVolume(TvPlayers.volume(screen) + VOLUME_STEP);
                }
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (vertical) {
                    skipVideo(1);
                } else {
                    setVolume(TvPlayers.volume(screen) - VOLUME_STEP);
                }
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public void removed() {
        TvPlayers.setFullscreen(screen, false);
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
