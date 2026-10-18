package dev.anomz.blockcinema.client.screen;

import java.util.Locale;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Where the TV is in its video, as a slider: follows playback, and jumps there when let go. Greyed out
 * for live streams and until the page first reports.
 */
final class TimeBar extends AbstractSliderButton {
    private final BlockPos screen;
    private boolean dragging;

    TimeBar(int x, int y, int width, BlockPos screen) {
        super(x, y, width, 20, Component.empty(), 0);
        this.screen = screen;
        refresh();
    }

    /** "1:02:03", or "2:03" under an hour. */
    static String format(double seconds) {
        long total = (long) Math.max(0, seconds);
        long hours = total / 3600;
        long minutes = total / 60 % 60;
        long secs = total % 60;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /** Follows playback; call every tick. */
    void refresh() {
        if (dragging) {
            return;
        }
        TvPlayers.Position position = TvPlayers.position(screen);
        active = position != null && position.seekable();
        value = active ? Math.min(1, position.time() / position.length()) : 0;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        TvPlayers.Position position = TvPlayers.position(screen);
        if (position == null) {
            setMessage(Component.translatable("screen." + BlockCinema.MODID + ".time_unknown"));
        } else if (!position.seekable()) {
            setMessage(Component.translatable("screen." + BlockCinema.MODID + ".live"));
        } else {
            setMessage(Component.literal(format(value * position.length()) + " / " + format(position.length())));
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        dragging = false;
        seekToValue();
    }

    @Override
    protected void applyValue() {
        // While dragging, only the label follows; the jump happens on release. Arrow keys jump at once.
        if (!dragging) {
            seekToValue();
        }
    }

    private void seekToValue() {
        TvPlayers.Position position = TvPlayers.position(screen);
        if (position != null && position.seekable()) {
            TvRemote.seek(screen, value * position.length());
        }
    }
}
