package dev.anomz.blockcinema.client.screen;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.LocalVideos;
import dev.anomz.blockcinema.client.browser.VideoLinks;
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

/**
 * The video files in the player's {@code blockcinema-videos} folder, to play on the screen. Only the
 * file's name is shared, so other players watch along if they have a file of that name too.
 */
public final class LocalFilesScreen extends PanelScreen {
    private static final int DOUBLE_CLICK_MILLIS = 300;
    private static final int ROW_H = 20;
    private static final int REFRESH_TICKS = 20;

    private final BlockPos screen;
    private FileList list;
    private TextButton play;
    @Nullable
    private TextButton queue;
    private List<String> shown = List.of();
    private Component error = Component.empty();
    private int ticks;

    public LocalFilesScreen(Screen parent, BlockPos screen) {
        super(Component.translatable(key("my_files")), parent);
        this.screen = screen;
        subtitle = Component.translatable(key("my_files_subtitle"));
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
        addRenderableWidget(new TextButton(x, bottomBarY(), 100, 20, Component.translatable(key("open_folder")), false,
                () -> Util.getPlatform().openPath(LocalVideos.folder())));
        list = addRenderableWidget(new FileList(minecraft, panelW - 2 * Ui.PAD, bottomBarY() - 20 - listTop, listTop));
        list.setX(panelX + Ui.PAD);
        list.fill(LocalVideos.list());
    }

    @Override
    public void tick() {
        // Files dropped into the folder while this is open show up by themselves.
        if (++ticks % REFRESH_TICKS == 0) {
            List<String> files = LocalVideos.list();
            if (!files.equals(shown)) {
                list.fill(files);
            }
        }
    }

    private void start(boolean queued) {
        FileList.Row row = list.getSelected();
        if (row == null) {
            return;
        }
        String format = VideoLinks.unplayableFile(row.name);
        if (format != null) {
            error = Component.translatable(key("bad_format"), format);
            return;
        }
        String page = TvPageRules.localFilePage(row.name);
        if (queued) {
            TvRemote.enqueue(screen, TvService.DIRECT_LINK, page, row.name);
        } else {
            TvRemote.play(screen, TvService.DIRECT_LINK, page);
        }
        minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (list.children().isEmpty()) {
            g.drawCenteredString(font, Component.translatable(key("my_files_empty"), LocalVideos.FOLDER), panelX + panelW / 2,
                    list.getY() + list.getHeight() / 2 - 4, Ui.TEXT_DIM);
        }
        g.drawString(font, font.plainSubstrByWidth(error.getString(), contentWidth()), contentLeft(), bottomBarY() - 14, Ui.DANGER,
                false);
    }

    private final class FileList extends ObjectSelectionList<FileList.Row> {
        FileList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_H);
        }

        void fill(List<String> files) {
            Row selected = getSelected();
            clearEntries();
            setSelected(null);
            shown = files;
            for (String name : files) {
                Row row = new Row(name);
                addEntry(row);
                if (selected != null && selected.name.equals(name)) {
                    setSelected(row);
                }
            }
        }

        @Override
        public void setSelected(@Nullable Row row) {
            super.setSelected(row);
            error = Component.empty();
            play.active = row != null;
            if (queue != null) {
                queue.active = row != null;
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
            final String name;
            private long lastClick;

            Row(String name) {
                this.name = name;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX,
                    int mouseY, boolean hovering, float partialTick) {
                boolean selected = getSelected() == this;
                if (selected || hovering) {
                    Ui.round(g, left - 2, top - 1, width + 4, height + 1, selected ? Ui.ACCENT_SOFT : Ui.SURFACE_HOVER);
                }
                Ui.icon(g, Icon.LINK, left + 3, top + (height - 12) / 2, 12, selected ? Ui.ACCENT_HOVER : Ui.TEXT_DIM);
                g.drawString(font, font.plainSubstrByWidth(name, width - 25), left + 21, top + (height - font.lineHeight) / 2, Ui.TEXT,
                        false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                FileList.this.setSelected(this);
                long now = Util.getMillis();
                if (now - lastClick < DOUBLE_CLICK_MILLIS) {
                    start(false);
                }
                lastClick = now;
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }
}
