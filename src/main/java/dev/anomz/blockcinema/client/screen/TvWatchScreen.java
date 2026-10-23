package dev.anomz.blockcinema.client.screen;

import org.lwjgl.glfw.GLFW;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Watching a TV in the world, locked onto it: the camera turns to the screen and stays there, the HUD and
 * the mouse cursor are hidden, the wheel (or up/down) changes the volume, a click (or Space) pauses, Esc
 * leaves. A short help line shows at the bottom for a moment, and the volume while it changes.
 */
public final class TvWatchScreen extends Screen {
    private static final int HELP_MILLIS = 4000;
    private static final int HELP_FADE_MILLIS = 1000;
    private static final float WHEEL_VOLUME_STEP = 0.05f;
    private static final float VOLUME_STEP = 0.1f;

    private final BlockPos screen;
    /** Whether this screen hid the HUD (so it shows it again when left). */
    private boolean hidGui;
    private long openedMillis;
    /** The volume after the last change, shown in place of the help for a moment. */
    private Component notice = Component.empty();
    private long noticeMillis;

    public TvWatchScreen(BlockPos screen) {
        super(Component.translatable(key("watch_here")));
        this.screen = screen;
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + "." + name;
    }

    @Override
    protected void init() {
        // Once, not again when the window is resized.
        if (openedMillis == 0) {
            openedMillis = Util.getMillis();
            lookAtScreen();
            hidGui = !minecraft.options.hideGui;
            minecraft.options.hideGui = true;
        }
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    private void lookAtScreen() {
        if (minecraft.player != null && minecraft.level != null && minecraft.level.getBlockEntity(screen) instanceof TvBlockEntity tv) {
            TvLayout layout = tv.layout(minecraft.level.getGameTime());
            if (!layout.invalid()) {
                minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, layout.bounds().getCenter());
            }
        }
    }

    private void changeVolume(float change) {
        TvPlayers.setVolume(screen, TvPlayers.volume(screen) + change);
        notice = Component.translatable(key("volume"), Math.round(TvPlayers.volume(screen) * 100));
        noticeMillis = Util.getMillis();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean noticeShows = noticeMillis > openedMillis;
        Component text = noticeShows ? notice : Component.translatable(key("watch_help"));
        long left = HELP_MILLIS - (Util.getMillis() - (noticeShows ? noticeMillis : openedMillis));
        int alpha = (int) (255 * Math.min(1, left / (float) HELP_FADE_MILLIS));
        if (alpha > 8) {
            graphics.drawCenteredString(font, text, width / 2, height - 14, alpha << 24 | 0xFFFFFF);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The world shows as it is; no blur or dimming.
    }

    @Override
    public void tick() {
        // Turned off, broken or too far away.
        if (TvRemote.source(screen) == null || minecraft.player == null || !minecraft.player.isAlive()) {
            onClose();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            changeVolume(scrollY > 0 ? WHEEL_VOLUME_STEP : -WHEEL_VOLUME_STEP);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            TvRemote.togglePause(screen);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> TvRemote.togglePause(screen);
            case GLFW.GLFW_KEY_UP -> changeVolume(VOLUME_STEP);
            case GLFW.GLFW_KEY_DOWN -> changeVolume(-VOLUME_STEP);
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public void removed() {
        if (hidGui) {
            minecraft.options.hideGui = false;
        }
        // Another screen may come next; it needs the cursor (back to the game grabs the mouse anyway).
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
