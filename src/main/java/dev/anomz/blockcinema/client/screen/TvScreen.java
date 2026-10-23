package dev.anomz.blockcinema.client.screen;

import java.util.List;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvQueueEntry;
import dev.anomz.blockcinema.TvServerConfig;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlock;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.Agreements;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.browser.VideoLinks;
import dev.anomz.blockcinema.client.jellyfin.JellyfinLogin;
import dev.anomz.blockcinema.client.screen.ui.Icon;
import dev.anomz.blockcinema.client.screen.ui.IconButton;
import dev.anomz.blockcinema.client.screen.ui.PanelScreen;
import dev.anomz.blockcinema.client.screen.ui.ServiceTile;
import dev.anomz.blockcinema.client.screen.ui.TextButton;
import dev.anomz.blockcinema.client.screen.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The TV's menu: what's on with media controls, and one card per service. Nothing else: there is no
 * address bar.
 */
public final class TvScreen extends PanelScreen {
    private static final int WIDTH = 300;
    private static final int CARD_H = 68;
    private static final int TILE_H = 40;
    private static final int GAP = 8;
    private static final int SKIP_SECONDS = 10;
    private static final int QUEUE_ROWS = 3;
    private static final int QUEUE_ROW_H = 14;

    /** The screen's origin; it moves when a floor or ceiling screen is turned. */
    private BlockPos screen;
    private TimeBar timeBar;
    private VolumeSlider volume;
    private boolean builtOn;
    /** Asked once per opening: a YouTube TV is on but this player hasn't agreed to YouTube's terms. */
    private boolean askedToAgree;
    private float volumeBeforeMute = 1;
    private int cardY;
    private int sectionY;
    private int queueY;
    private List<TvQueueEntry> builtQueue = List.of();
    private int statusY;
    private Component status = Component.empty();

    /** {@code screen} is the origin of the clicked TV's screen (see {@code TvLayout}). */
    public TvScreen(BlockPos screen) {
        super(Component.translatable(key("tv")), null);
        this.screen = screen;
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + "." + name;
    }

    @Override
    protected int panelWidth() {
        return WIDTH;
    }

    @Override
    protected int panelHeight() {
        boolean on = TvRemote.source(screen) != null;
        return Ui.HEADER + Ui.PAD + (on ? CARD_H + 12 : 0) + queueHeight() + 14 + 2 * TILE_H + GAP + 8 + 14 + Ui.PAD;
    }

    @Override
    protected void initContent() {
        TvSource source = TvRemote.source(screen);
        builtOn = source != null;
        subtitle = source == null ? Component.translatable(key("off"))
                : Component.translatable(key("now_playing"), Component.translatable(source.service().translationKey()), source.ownerName());
        // Header buttons, right to left after close: rotate (floor and ceiling screens), watch in the world.
        int iconSize = 18;
        int headerX = panelX + panelW - Ui.PAD - iconSize + 4;
        int headerY = panelY + (Ui.HEADER - iconSize) / 2;
        if (lyingFlat()) {
            headerX -= iconSize + 4;
            addRenderableWidget(new IconButton(headerX, headerY, iconSize, Icon.ROTATE, Component.translatable(key("rotate")),
                    IconButton.Style.GHOST, () -> TvRemote.rotate(screen)));
        }
        if (source != null && TvPlayers.isPlaying(screen)) {
            headerX -= iconSize + 4;
            addRenderableWidget(new IconButton(headerX, headerY, iconSize, Icon.EYE, Component.translatable(key("watch_here")),
                    IconButton.Style.GHOST, () -> minecraft.setScreen(new TvWatchScreen(screen))));
        }
        int x = contentLeft();
        int w = contentWidth();
        int y = contentTop();
        timeBar = null;
        if (source != null) {
            cardY = y;
            addControls(x, y, w);
            y += CARD_H + 12;
        }
        builtQueue = queue();
        queueY = y;
        if (!builtQueue.isEmpty()) {
            addQueue(x, y, w);
            y += queueHeight();
        }
        sectionY = y;
        y += 14;
        int tileW = (w - GAP) / 2;
        TvService[] services = TvService.values();
        for (int i = 0; i < services.length; i++) {
            TvService service = services[i];
            boolean enabled = TvServerConfig.enabled(service);
            ServiceTile tile = addRenderableWidget(new ServiceTile(x + i % 2 * (tileW + GAP), y + i / 2 * (TILE_H + GAP), tileW, TILE_H,
                    icon(service), Component.translatable(service.translationKey()),
                    // The server's settings (synced to this client); the server checks them again.
                    Component.translatable(enabled ? key("tile." + service.id()) : key("service_disabled")), () -> open(service)));
            tile.active = enabled;
        }
        statusY = y + 2 * TILE_H + GAP + 8;
        // Always reachable, as YouTube's policies ask.
        Component privacy = Component.translatable(key("privacy"));
        int privacyW = font.width(privacy) + 12;
        addRenderableWidget(new TextButton(x + w - privacyW, statusY - 3, privacyW, 14, privacy, false,
                () -> minecraft.setScreen(new AgreementScreen(this, null))));
    }

    private List<TvQueueEntry> queue() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(screen) instanceof TvBlockEntity tv
                ? List.copyOf(tv.queue()) : List.of();
    }

    private int queueHeight() {
        int rows = Math.min(queue().size(), QUEUE_ROWS);
        return rows == 0 ? 0 : 14 + rows * QUEUE_ROW_H + (queue().size() > QUEUE_ROWS ? QUEUE_ROW_H : 0) + 8;
    }

    /** "Up next": the first queued videos with a remove button each, and skip to the next one. */
    private void addQueue(int x, int y, int w) {
        Component skip = Component.translatable(key("skip_to_next"));
        int skipW = font.width(skip) + 12;
        addRenderableWidget(new TextButton(x + w - skipW, y - 3, skipW, 14, skip, false, () -> TvRemote.next(screen)));
        for (int i = 0; i < Math.min(builtQueue.size(), QUEUE_ROWS); i++) {
            int index = i;
            addRenderableWidget(new IconButton(x + w - 14, y + 14 + i * QUEUE_ROW_H, 12, Icon.CLOSE,
                    Component.translatable(key("remove_from_queue")), IconButton.Style.DANGER, () -> TvRemote.removeQueued(screen, index)));
        }
    }

    private static Icon icon(TvService service) {
        return switch (service) {
            case YOUTUBE -> Icon.VIDEO;
            case TWITCH -> Icon.LIVE;
            case JELLYFIN -> Icon.LIBRARY;
            case DIRECT_LINK -> Icon.LINK;
        };
    }

    /** The "now playing" card: seek bar and times, then skip, play/pause, volume, fullscreen and power. */
    private void addControls(int x, int y, int w) {
        timeBar = addRenderableWidget(new TimeBar(x + 6, y + 8, w - 12, screen));
        int rowY = y + 38;
        int mid = x + w / 2;
        addRenderableWidget(new IconButton(mid - 12, rowY, 24, () -> paused() ? Icon.PLAY : Icon.PAUSE,
                Component.translatable(key("play_pause")), IconButton.Style.PRIMARY, () -> TvRemote.togglePause(screen)));
        addRenderableWidget(new IconButton(mid - 12 - 6 - 20, rowY + 2, 20, Icon.BACK,
                Component.translatable(key("skip_back"), SKIP_SECONDS), IconButton.Style.GHOST, () -> TvRemote.skip(screen, -SKIP_SECONDS)));
        addRenderableWidget(new IconButton(mid + 12 + 6, rowY + 2, 20, Icon.FORWARD,
                Component.translatable(key("skip_forward"), SKIP_SECONDS), IconButton.Style.GHOST, () -> TvRemote.skip(screen, SKIP_SECONDS)));
        addRenderableWidget(new IconButton(x + 6, rowY + 2, 20, () -> TvPlayers.volume(screen) <= 0 ? Icon.MUTE : Icon.VOLUME,
                Component.translatable(key("mute")), IconButton.Style.GHOST, this::toggleMute));
        volume = addRenderableWidget(new VolumeSlider(x + 28, rowY + 6, 44, screen));
        addRenderableWidget(new IconButton(mid - 12 - 6 - 20 - 4 - 20, rowY + 2, 20, Icon.PREVIOUS,
                Component.translatable(key("previous_video")), IconButton.Style.GHOST, () -> skipVideo(-1)));
        addRenderableWidget(new IconButton(mid + 12 + 6 + 20 + 4, rowY + 2, 20, Icon.NEXT,
                Component.translatable(key("next_video")), IconButton.Style.GHOST, () -> skipVideo(1)));
        IconButton fullscreen = addRenderableWidget(new IconButton(x + w - 6 - 20 - 4 - 20, rowY + 2, 20, Icon.FULLSCREEN,
                Component.translatable(key("fullscreen")), IconButton.Style.GHOST, () -> minecraft.setScreen(new TvFullscreenScreen(this, screen))));
        // Someone else's Jellyfin has no picture here.
        fullscreen.active = TvPlayers.isPlaying(screen);
        addRenderableWidget(new IconButton(x + w - 6 - 20, rowY + 2, 20, Icon.POWER,
                Component.translatable(key("turn_off")), IconButton.Style.DANGER, () -> {
                    TvRemote.turnOff(screen);
                    minecraft.setScreen(null);
                }));
    }

    /** Whether this is a floor or ceiling screen, whose picture can be turned. */
    private boolean lyingFlat() {
        BlockState state = minecraft.level == null ? null : minecraft.level.getBlockState(screen);
        return state != null && state.getBlock() instanceof TvBlock
                && state.getValue(BlockStateProperties.ORIENTATION).front().getAxis() == Direction.Axis.Y;
    }

    private boolean paused() {
        TvSource source = TvRemote.source(screen);
        return source == null || source.paused();
    }

    private void skipVideo(int direction) {
        Component problem = TvRemote.skipVideo(screen, direction);
        if (problem != null) {
            timeBar.notice(problem);
        }
    }

    private void toggleMute() {
        float now = TvPlayers.volume(screen);
        if (now > 0) {
            volumeBeforeMute = now;
            TvPlayers.setVolume(screen, 0);
        } else {
            TvPlayers.setVolume(screen, volumeBeforeMute);
        }
        volume.show(TvPlayers.volume(screen));
    }

    @Override
    public void tick() {
        TvSource playing = TvRemote.source(screen);
        if (!askedToAgree && playing != null && playing.service() == TvService.YOUTUBE && !Agreements.youtube()) {
            askedToAgree = true;
            minecraft.setScreen(new AgreementScreen(this, () -> minecraft.setScreen(this)));
            return;
        }
        // Turned: the screen's origin is another corner now.
        if (minecraft.level != null && minecraft.level.getBlockEntity(screen) instanceof TvBlockEntity tv) {
            TvLayout layout = tv.layout(minecraft.level.getGameTime());
            if (!layout.invalid() && !layout.origin().equals(screen)) {
                screen = layout.origin();
                rebuildWidgets();
                return;
            }
        }
        // Turned on or off, or the queue changed, meanwhile (maybe by another player): rebuild.
        if ((TvRemote.source(screen) != null) != builtOn || !queue().equals(builtQueue)) {
            rebuildWidgets();
        }
        if (timeBar != null) {
            timeBar.refresh();
        }
    }

    private void open(TvService service) {
        if (!TvPlayers.ready()) {
            status = Component.translatable(key("browser_not_ready"));
            return;
        }
        switch (service) {
            case YOUTUBE -> {
                Runnable link = () -> minecraft.setScreen(new LinkScreen(this, screen, service, VideoLinks::youtube));
                if (Agreements.youtube()) {
                    link.run();
                } else {
                    minecraft.setScreen(new AgreementScreen(this, link));
                }
            }
            case TWITCH -> minecraft.setScreen(new LinkScreen(this, screen, service, VideoLinks::twitch));
            case JELLYFIN -> {
                JellyfinLogin login = JellyfinLogin.current();
                minecraft.setScreen(login != null
                        ? new JellyfinBrowseScreen(this, screen, login, null, Component.translatable(service.translationKey()))
                        : new JellyfinLoginScreen(this, screen));
            }
            case DIRECT_LINK -> minecraft.setScreen(new LinkScreen(this, screen, service, VideoLinks::direct));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (timeBar != null) {
            Ui.box(g, contentLeft(), cardY, contentWidth(), CARD_H, Ui.SURFACE, Ui.DIVIDER);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = contentLeft();
        int w = contentWidth();
        if (timeBar != null) {
            int timeY = cardY + 23;
            TvPlayers.Position position = TvPlayers.position(screen);
            if (position != null && !position.seekable()) {
                // Live: a small badge instead of times.
                Component live = Component.translatable(key("live"));
                Ui.round(g, x + 9, timeY - 2, font.width(live) + 8, font.lineHeight + 3, Ui.ACCENT);
                g.drawString(font, live, x + 13, timeY, 0xFFFFFFFF, false);
            } else {
                double shown = timeBar.shownSeconds();
                String left = shown < 0 ? "--:--" : TimeBar.format(shown);
                String right = position == null ? "--:--" : TimeBar.format(position.length());
                g.drawString(font, left, x + 10, timeY, Ui.TEXT_DIM, false);
                g.drawString(font, right, x + w - 10 - font.width(right), timeY, Ui.TEXT_DIM, false);
            }
        }
        if (!builtQueue.isEmpty()) {
            g.drawString(font, Component.translatable(key("up_next")), x, queueY, Ui.TEXT_FAINT, false);
            for (int i = 0; i < Math.min(builtQueue.size(), QUEUE_ROWS); i++) {
                TvQueueEntry entry = builtQueue.get(i);
                String name = entry.title().isEmpty() ? VideoLinks.label(entry.page()) : entry.title();
                String line = Component.translatable(entry.service().translationKey()).getString() + " · " + name;
                int rowY = queueY + 14 + i * QUEUE_ROW_H + 2;
                g.drawString(font, font.plainSubstrByWidth(line, w - 24 - 70), x, rowY, Ui.TEXT, false);
                String by = entry.ownerName();
                g.drawString(font, font.plainSubstrByWidth(by, 64), x + w - 20 - font.width(font.plainSubstrByWidth(by, 64)), rowY,
                        Ui.TEXT_FAINT, false);
            }
            if (builtQueue.size() > QUEUE_ROWS) {
                g.drawString(font, Component.translatable(key("more_queued"), builtQueue.size() - QUEUE_ROWS), x,
                        queueY + 14 + QUEUE_ROWS * QUEUE_ROW_H + 2, Ui.TEXT_DIM, false);
            }
        }
        g.drawString(font, Component.translatable(key("watch")), x, sectionY, Ui.TEXT_FAINT, false);
        String error = TvPlayers.error(screen);
        if (error != null && status.getString().isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(key("error." + error)).getString(), w - 110),
                    x, statusY, Ui.DANGER, false);
        }
        if (!status.getString().isEmpty()) {
            g.drawString(font, status, contentLeft(), statusY, Ui.TEXT_DIM, false);
        }
    }
}
