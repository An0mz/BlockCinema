package dev.anomz.blockcinema.client.screen;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvServerConfig;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.Agreements;
import dev.anomz.blockcinema.client.SavedVideos;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.screen.ui.Icon;
import dev.anomz.blockcinema.client.screen.ui.PanelScreen;
import dev.anomz.blockcinema.client.screen.ui.TextButton;
import dev.anomz.blockcinema.client.screen.ui.Ui;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** The player's favorites, then what they played lately; the star on a row keeps it or lets it go. */
public final class SavedScreen extends PanelScreen {
    private static final int DOUBLE_CLICK_MILLIS = 300;
    private static final int ROW_H = 20;
    private static final int STAR = 12;

    private final BlockPos screen;
    private VideoList list;
    private TextButton play;
    @Nullable
    private TextButton queue;

    public SavedScreen(Screen parent, BlockPos screen) {
        super(Component.translatable(key("saved")), parent);
        this.screen = screen;
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + "." + name;
    }

    @Override
    protected int panelWidth() {
        return 420;
    }

    @Override
    protected int panelHeight() {
        return 300;
    }

    private int bottomBarY() {
        return panelY + panelH - Ui.PAD - 20;
    }

    @Override
    protected void initContent() {
        int x = contentLeft();
        int w = contentWidth();
        int listTop = contentTop() - 4;
        play = addRenderableWidget(new TextButton(x + w - 90, bottomBarY(), 90, 20, Component.translatable(key("play")), true,
                () -> start(false)));
        queue = TvRemote.source(screen) == null ? null : addRenderableWidget(new TextButton(x + w - 90 - 6 - 90, bottomBarY(), 90, 20,
                Component.translatable(key("add_to_queue")), false, () -> start(true)));
        addRenderableWidget(new TextButton(x, bottomBarY(), 100, 20, Component.translatable(key("clear_history")), false, () -> {
            SavedVideos.get().clearHistory();
            list.fill();
        })).active = !SavedVideos.get().history().isEmpty();
        list = addRenderableWidget(new VideoList(minecraft, panelW - 2 * Ui.PAD, bottomBarY() - 6 - listTop, listTop));
        list.setX(panelX + Ui.PAD);
        list.fill();
    }

    private void start(boolean queued) {
        VideoList.Row row = list.getSelected();
        if (row == null || !TvServerConfig.enabled(row.video.service())) {
            return;
        }
        SavedVideos.Entry video = row.video;
        if (video.service() == TvService.YOUTUBE && !Agreements.youtube()) {
            minecraft.setScreen(new AgreementScreen(this, () -> minecraft.setScreen(this)));
        } else if (queued) {
            TvRemote.enqueue(screen, video.service(), video.page(), video.title());
            minecraft.setScreen(null);
        } else {
            TvRemote.play(screen, video.service(), video.page());
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (list.children().isEmpty()) {
            g.drawCenteredString(font, Component.translatable(key("saved_empty")), panelX + panelW / 2,
                    list.getY() + list.getHeight() / 2 - 4, Ui.TEXT_DIM);
        }
    }

    private final class VideoList extends ObjectSelectionList<VideoList.Row> {
        VideoList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_H);
        }

        void fill() {
            clearEntries();
            setSelected(null);
            SavedVideos saved = SavedVideos.get();
            saved.favorites().forEach(video -> addEntry(new Row(video, true)));
            saved.history().stream().filter(video -> !saved.isFavorite(video.page())).forEach(video -> addEntry(new Row(video, false)));
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            boolean playable = row != null && TvServerConfig.enabled(row.video.service());
            play.active = playable;
            if (queue != null) {
                queue.active = playable;
            }
        }

        @Override
        public int getRowWidth() {
            return width - 14;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + width - 6;
        }

        @Override
        protected void renderListBackground(GuiGraphics g) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics g) {
        }

        @Override
        protected void renderSelection(GuiGraphics g, int top, int width, int height, int outerColor, int innerColor) {
        }

        final class Row extends ObjectSelectionList.Entry<Row> {
            final SavedVideos.Entry video;
            private final boolean favorite;
            private int starX;
            private long lastClick;

            Row(SavedVideos.Entry video, boolean favorite) {
                this.video = video;
                this.favorite = favorite;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX,
                    int mouseY, boolean hovering, float partialTick) {
                boolean selected = getSelected() == this;
                if (selected || hovering) {
                    Ui.round(g, left - 2, top - 1, width + 4, height + 1, selected ? Ui.ACCENT_SOFT : Ui.SURFACE_HOVER);
                }
                int mid = top + (height - font.lineHeight) / 2;
                Ui.icon(g, TvScreen.icon(video.service()), left + 3, top + (height - 12) / 2, 12, selected ? Ui.ACCENT_HOVER : Ui.TEXT_DIM);
                starX = left + width - 4 - STAR;
                g.drawString(font, font.plainSubstrByWidth(video.title(), starX - 8 - (left + 21)), left + 21, mid, Ui.TEXT, false);
                Ui.icon(g, Icon.STAR, starX, top + (height - STAR) / 2, STAR, favorite ? Ui.ACCENT_HOVER : Ui.TEXT_FAINT);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (mouseX >= starX - 4) {
                    SavedVideos.get().toggleFavorite(video.service(), video.page());
                    fill();
                    return true;
                }
                VideoList.this.setSelected(this);
                long now = Util.getMillis();
                if (now - lastClick < DOUBLE_CLICK_MILLIS) {
                    start(false);
                }
                lastClick = now;
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(video.title());
            }
        }
    }
}
