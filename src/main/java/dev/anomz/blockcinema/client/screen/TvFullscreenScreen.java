package dev.anomz.blockcinema.client.screen;

import org.lwjgl.glfw.GLFW;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A TV's picture filling the game window, letterboxed. Nothing is drawn over the picture (YouTube's
 * terms forbid covering its player); the key help goes in the bars beside or below it, when there are any.
 * Space pauses, left and right skip 10 seconds, up and down change the TV's volume, Esc goes back.
 */
public final class TvFullscreenScreen extends Screen {
    private static final float VOLUME_STEP = 0.1f;
    private static final int SKIP_SECONDS = 10;

    private final Screen parent;
    private final BlockPos screen;

    public TvFullscreenScreen(Screen parent, BlockPos screen) {
        super(Component.translatable("screen." + BlockCinema.MODID + ".fullscreen"));
        this.parent = parent;
        this.screen = screen;
    }

    @Override
    protected void init() {
        TvPlayers.setFullscreen(screen, true);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        ResourceLocation picture = TvPlayers.texture(screen);
        // 16:9, as large as fits.
        int picW = Math.min(width, height * 16 / 9);
        int picH = picW * 9 / 16;
        int x = (width - picW) / 2;
        int y = (height - picH) / 2;
        if (picture != null) {
            TvPlayers.markSeen(screen);
            graphics.blit(picture, x, y, picW, picH, 0, 0, TvPlayers.WIDTH, TvPlayers.HEIGHT, TvPlayers.WIDTH, TvPlayers.HEIGHT);
        }
        TvPlayers.Position position = TvPlayers.position(screen);
        String time = position == null ? "" : position.seekable()
                ? TimeBar.format(position.time()) + " / " + TimeBar.format(position.length()) + "   " : "";
        Component help = Component.translatable("screen." + BlockCinema.MODID + ".fullscreen_help",
                time, Math.round(TvPlayers.volume(screen) * 100));
        if (height - (y + picH) >= font.lineHeight + 4) {
            graphics.drawCenteredString(font, help, width / 2, y + picH + 2, 0x808080);
        } else if (x >= font.width(help) + 8) {
            graphics.drawString(font, help, 4, height - font.lineHeight - 4, 0x808080);
        }
    }

    @Override
    public void tick() {
        // Turned off, broken or too far away.
        if (TvRemote.source(screen) == null) {
            onClose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The picture covers the world; no blur or dimming behind it.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> TvRemote.togglePause(screen);
            case GLFW.GLFW_KEY_UP -> TvPlayers.setVolume(screen, TvPlayers.volume(screen) + VOLUME_STEP);
            case GLFW.GLFW_KEY_DOWN -> TvPlayers.setVolume(screen, TvPlayers.volume(screen) - VOLUME_STEP);
            case GLFW.GLFW_KEY_LEFT -> TvRemote.skip(screen, -SKIP_SECONDS);
            case GLFW.GLFW_KEY_RIGHT -> TvRemote.skip(screen, SKIP_SECONDS);
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
