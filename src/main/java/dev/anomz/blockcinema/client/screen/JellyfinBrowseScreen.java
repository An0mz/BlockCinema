package dev.anomz.blockcinema.client.screen;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.browser.VideoLinks;
import dev.anomz.blockcinema.client.jellyfin.JellyfinApi;
import dev.anomz.blockcinema.client.jellyfin.JellyfinLogin;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * One level of the player's Jellyfin library: the libraries themselves at the top, then folders,
 * series and seasons. Folders open another level; videos play on the TV.
 */
public final class JellyfinBrowseScreen extends Screen {
    private static final int DOUBLE_CLICK_MILLIS = 300;

    private final Screen parent;
    private final BlockPos screen;
    private final JellyfinLogin login;
    @Nullable
    private final String folderId;
    private final JellyfinApi api;
    private List<JellyfinApi.Item> items;
    private ItemList list;
    private Button open;
    private Component status = Component.empty();
    private boolean loading;
    private boolean closed;

    /** {@code folderId} null lists the user's libraries. */
    public JellyfinBrowseScreen(Screen parent, BlockPos screen, JellyfinLogin login, @Nullable String folderId,
            Component title) {
        super(title);
        this.parent = parent;
        this.screen = screen;
        this.login = login;
        this.folderId = folderId;
        this.api = JellyfinApi.of(login, JellyfinLoginScreen.version());
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + ".jellyfin." + name;
    }

    @Override
    protected void init() {
        // Back from a folder opened from here.
        closed = false;
        list = addRenderableWidget(new ItemList(minecraft, width, height - 64, 32));
        if (items != null) {
            items.forEach(item -> list.add(item));
        }
        int buttonW = 100;
        int y = height - 26;
        boolean root = folderId == null;
        int count = root ? 3 : 2;
        int left = (width - count * buttonW - (count - 1) * 8) / 2;
        open = addRenderableWidget(Button.builder(Component.translatable(key("open")), button -> openSelected())
                .bounds(left, y, buttonW, 20).build());
        open.active = false;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + buttonW + 8, y, buttonW, 20).build());
        if (root) {
            addRenderableWidget(Button.builder(Component.translatable(key("log_out")), button -> logOut())
                    .bounds(left + 2 * (buttonW + 8), y, buttonW, 20).build());
        }
        if (items == null && !loading) {
            load();
        }
    }

    private void load() {
        loading = true;
        status = Component.translatable(key("loading"));
        (folderId == null ? api.libraries(login.userId()) : api.children(login.userId(), folderId))
                .whenComplete((result, error) -> minecraft.execute(() -> {
                    loading = false;
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        BlockCinema.LOGGER.warn("Couldn't list the Jellyfin library", error);
                        status = Component.translatable(key("load_failed")).withStyle(ChatFormatting.RED);
                        return;
                    }
                    items = result;
                    result.forEach(item -> list.add(item));
                    status = result.isEmpty() ? Component.translatable(key("empty")) : Component.empty();
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
        status = Component.translatable(key("starting"));
        api.playback(login.userId(), item.id()).whenComplete((playback, error) -> minecraft.execute(() -> {
            if (error != null) {
                BlockCinema.LOGGER.warn("Jellyfin couldn't play {}", item.id(), error);
                if (!closed) {
                    open.active = true;
                    status = Component.translatable(key("play_failed")).withStyle(ChatFormatting.RED);
                }
                return;
            }
            TvPlayers.play(screen, VideoLinks.videoPage(playback.url(), api.authorization()),
                    () -> api.reportStopped(playback));
            api.reportStarted(playback);
            if (!closed) {
                minecraft.setScreen(null);
            }
        }));
    }

    private void logOut() {
        JellyfinLogin.forget();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, height / 2, 0xAAAAAA);
    }

    @Override
    public void removed() {
        closed = true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class ItemList extends ObjectSelectionList<ItemList.Entry> {
        ItemList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 18);
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
            open.setMessage(Component.translatable(key(entry != null && !entry.item.folder() ? "play" : "open")));
        }

        @Override
        public int getRowWidth() {
            return Math.min(400, width - 40);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final JellyfinApi.Item item;
            private long lastClick;

            Entry(JellyfinApi.Item item) {
                this.item = item;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX,
                    int mouseY, boolean hovering, float partialTick) {
                String name = item.folder() ? item.name() + "/" : item.name();
                graphics.drawString(font, font.plainSubstrByWidth(name, width - 50), left + 4, top + 4, 0xFFFFFF);
                graphics.drawString(font, item.detail(), left + width - 4 - font.width(item.detail()), top + 4, 0x808080);
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
