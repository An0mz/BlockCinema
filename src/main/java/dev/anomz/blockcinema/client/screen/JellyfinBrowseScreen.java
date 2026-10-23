package dev.anomz.blockcinema.client.screen;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.jellyfin.JellyfinApi;
import dev.anomz.blockcinema.client.jellyfin.JellyfinLogin;
import dev.anomz.blockcinema.client.jellyfin.JellyfinWatch;
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
 * One level of the player's Jellyfin library: the libraries themselves at the top, then folders,
 * series and seasons. Folders open another level; videos play on the TV.
 */
public final class JellyfinBrowseScreen extends PanelScreen {
    private static final int DOUBLE_CLICK_MILLIS = 300;
    private static final int ROW_H = 20;

    private final BlockPos screen;
    private JellyfinLogin login;
    @Nullable
    private final String folderId;
    private final JellyfinApi api;
    private List<JellyfinApi.Item> items;
    private ItemList list;
    private TextButton open;
    @Nullable
    private TextButton queue;
    private Component status = Component.empty();
    private boolean statusIsError;
    private boolean loading;
    private boolean closed;

    /** {@code folderId} null lists the user's libraries. */
    public JellyfinBrowseScreen(Screen parent, BlockPos screen, JellyfinLogin login, @Nullable String folderId,
            Component title) {
        super(title, parent);
        this.screen = screen;
        this.login = login;
        this.folderId = folderId;
        this.api = JellyfinApi.of(login, JellyfinApi.appVersion());
        if (folderId == null) {
            subtitle = Component.translatable(key("logged_in_as"), login.userName());
        }
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + ".jellyfin." + name;
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
        // Back from a folder opened from here.
        closed = false;
        int listTop = contentTop() - 4;
        list = addRenderableWidget(new ItemList(minecraft, panelW - 2 * Ui.PAD, bottomBarY() - 8 - listTop, listTop));
        list.setX(panelX + Ui.PAD);
        if (items != null) {
            items.forEach(item -> list.add(item));
        }
        int x = contentLeft();
        int w = contentWidth();
        open = addRenderableWidget(new TextButton(x + w - 90, bottomBarY(), 90, 20, Component.translatable(key("open")), true,
                this::openSelected));
        open.active = false;
        if (TvRemote.source(screen) != null) {
            // Something plays already: a video can wait its turn instead.
            queue = addRenderableWidget(new TextButton(x + w - 90 - 6 - 90, bottomBarY(), 90, 20,
                    Component.translatable("screen." + BlockCinema.MODID + ".add_to_queue"), false, this::enqueueSelected));
            queue.active = false;
        } else {
            queue = null;
        }
        if (folderId == null) {
            addRenderableWidget(new TextButton(x, bottomBarY(), 80, 20, Component.translatable(key("log_out")), false, this::logOut));
        }
        if (items == null && !loading) {
            load();
        }
    }

    private void setStatus(Component message, boolean error) {
        status = message;
        statusIsError = error;
    }

    private void load() {
        loading = true;
        setStatus(Component.translatable(key("loading")), false);
        (folderId == null ? api.libraries(login.userId()) : api.children(login.userId(), folderId))
                .whenComplete((result, error) -> minecraft.execute(() -> {
                    loading = false;
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        BlockCinema.LOGGER.warn("Couldn't list the Jellyfin library", error);
                        setStatus(Component.translatable(key("load_failed")), true);
                        return;
                    }
                    items = result;
                    result.forEach(item -> list.add(item));
                    setStatus(result.isEmpty() ? Component.translatable(key("empty")) : Component.empty(), false);
                }));
    }

    private void openSelected() {
        ItemList.Entry entry = list.getSelected();
        if (entry == null) {
            return;
        }
        JellyfinApi.Item item = entry.item;
        if (item.folder()) {
            minecraft.setScreen(new JellyfinBrowseScreen(this, screen, login, item.id(), Component.literal(item.name())));
            return;
        }
        open.active = false;
        setStatus(Component.translatable(key("starting")), false);
        // The server's ID is what the TV shares. Jellyfin keeps the watch position itself: start where it
        // says. Everybody logged in to this server, this player included, then streams it with their own login.
        withServerId(id -> {
            TvRemote.playJellyfin(screen, id, item.id(), item.resumeSeconds());
            if (!closed) {
                minecraft.setScreen(null);
            }
        });
    }

    private void enqueueSelected() {
        ItemList.Entry entry = list.getSelected();
        if (entry == null || entry.item.folder()) {
            return;
        }
        JellyfinApi.Item item = entry.item;
        withServerId(id -> {
            TvRemote.enqueue(screen, TvService.JELLYFIN, TvPageRules.jellyfinPage(id, item.id()), item.name());
            if (!closed) {
                minecraft.setScreen(null);
            }
        });
    }

    /** Runs {@code action} with the server's ID (fetched once for logins saved by older versions). */
    private void withServerId(Consumer<String> action) {
        CompletableFuture<String> serverId = login.serverId().isEmpty()
                ? api.serverId() : CompletableFuture.completedFuture(login.serverId());
        serverId.whenComplete((id, error) -> minecraft.execute(() -> {
            if (error != null) {
                BlockCinema.LOGGER.warn("Couldn't reach the Jellyfin server", error);
                if (!closed) {
                    open.active = true;
                    setStatus(Component.translatable(key("play_failed")), true);
                }
                return;
            }
            if (login.serverId().isEmpty()) {
                login = login.withServerId(id);
                login.save();
            }
            action.accept(id);
        }));
    }

    private void logOut() {
        JellyfinLogin.forget();
        JellyfinWatch.clear();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!status.getString().isEmpty()) {
            int y = list.children().isEmpty() ? list.getY() + list.getHeight() / 2 - 4 : bottomBarY() + 6;
            g.drawCenteredString(font, status, panelX + panelW / 2, y, statusIsError ? Ui.DANGER : Ui.TEXT_DIM);
        }
    }

    @Override
    public void removed() {
        closed = true;
    }

    private final class ItemList extends ObjectSelectionList<ItemList.Entry> {
        ItemList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, ROW_H);
        }

        void add(JellyfinApi.Item item) {
            addEntry(new Entry(item));
        }

        @Override
        public void setSelected(@Nullable Entry entry) {
            super.setSelected(entry);
            if (open == null) {
                return;
            }
            open.active = entry != null;
            if (queue != null) {
                queue.active = entry != null && !entry.item.folder();
            }
            open.setMessage(Component.translatable(key(entry != null && !entry.item.folder() ? "play" : "open")));
        }

        @Override
        public int getRowWidth() {
            return width - 14;
        }

        @Override
        protected int getScrollbarPosition() {
            return getX() + width - 6;
        }

        // The panel is the background: no dirt, separators or vanilla selection box.
        @Override
        protected void renderListBackground(GuiGraphics g) {
        }

        @Override
        protected void renderListSeparators(GuiGraphics g) {
        }

        @Override
        protected void renderSelection(GuiGraphics g, int top, int width, int height, int outerColor, int innerColor) {
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final JellyfinApi.Item item;
            private long lastClick;

            Entry(JellyfinApi.Item item) {
                this.item = item;
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX,
                    int mouseY, boolean hovering, float partialTick) {
                boolean selected = getSelected() == this;
                if (selected || hovering) {
                    Ui.round(g, left - 2, top - 1, width + 4, height + 1, selected ? Ui.ACCENT_SOFT : Ui.SURFACE_HOVER);
                }
                int mid = top + (height - font.lineHeight) / 2;
                Ui.icon(g, item.folder() ? Icon.FOLDER : Icon.FILM, left + 3, top + (height - 12) / 2, 12,
                        selected ? Ui.ACCENT_HOVER : Ui.TEXT_DIM);
                String detail = item.resumeSeconds() > 0
                        ? Component.translatable(key("resume_at"), TimeBar.format(item.resumeSeconds())).getString()
                        : item.detail();
                int detailW = font.width(detail);
                g.drawString(font, font.plainSubstrByWidth(item.name(), width - 30 - detailW - 8), left + 21, mid, Ui.TEXT, false);
                g.drawString(font, detail, left + width - 4 - detailW, mid, item.resumeSeconds() > 0 ? Ui.ACCENT_HOVER : Ui.TEXT_FAINT, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                ItemList.this.setSelected(this);
                long now = Util.getMillis();
                if (now - lastClick < DOUBLE_CLICK_MILLIS) {
                    openSelected();
                }
                lastClick = now;
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(item.name());
            }
        }
    }
}
