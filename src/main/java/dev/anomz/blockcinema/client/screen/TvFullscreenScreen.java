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
    private final SkipGate scrollGate = new SkipGate();

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
        // 16:9, or 9:16 on a portrait screen, as large as fits.
        boolean portrait = TvPlayers.portrait(screen);
        int picW = portrait ? Math.min(width, height * 9 / 16) : Math.min(width, height * 16 / 9);
        int picH = portrait ? picW * 16 / 9 : picW * 9 / 16;
        int x = (width - picW) / 2;
        int y = (height - picH) / 2;
        if (picture != null) {
            TvPlayers.markSeen(screen);
            // The whole texture, whatever size the browser is.
            graphics.blit(picture, x, y, picW, picH, 0, 0, 16, 9, 16, 9);
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

    /** Shorts-style: the wheel goes to the next (down) or previous (up) video. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && scrollGate.allow()) {
            TvRemote.skipVideo(screen, scrollY < 0 ? 1 : -1);
        }
        return true;
    }

    /** A click goes to the next video too. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && scrollGate.allow()) {
            TvRemote.skipVideo(screen, 1);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
