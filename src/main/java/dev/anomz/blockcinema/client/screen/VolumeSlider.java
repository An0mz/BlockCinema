package dev.anomz.blockcinema.client.screen;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.screen.ui.UiSlider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The TV's own volume, for this player only. */
final class VolumeSlider extends UiSlider {
    private final BlockPos screen;

    VolumeSlider(int x, int y, int width, BlockPos screen) {
        super(x, y, width, 12, TvPlayers.volume(screen));
        this.screen = screen;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable("screen." + BlockCinema.MODID + ".volume", Math.round(value * 100)));
    }

    @Override
    protected void applyValue() {
        TvPlayers.setVolume(screen, (float) value);
    }
}
