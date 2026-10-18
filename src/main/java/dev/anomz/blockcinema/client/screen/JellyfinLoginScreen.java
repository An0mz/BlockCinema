package dev.anomz.blockcinema.client.screen;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.jellyfin.JellyfinApi;
import dev.anomz.blockcinema.client.jellyfin.JellyfinLogin;
import dev.anomz.blockcinema.client.screen.ui.PanelScreen;
import dev.anomz.blockcinema.client.screen.ui.TextButton;
import dev.anomz.blockcinema.client.screen.ui.TextField;
import dev.anomz.blockcinema.client.screen.ui.Ui;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Logs in to the player's Jellyfin server with Quick Connect: the screen shows a code, the player
 * approves it in any other Jellyfin app, and no password is ever typed into Minecraft.
 */
public final class JellyfinLoginScreen extends PanelScreen {
    private static final long POLL_SECONDS = 2;
    /** Jellyfin forgets Quick Connect codes after a while; stop asking well before that. */
    private static final long GIVE_UP_MILLIS = 5 * 60 * 1000;
    private static final int CODE_BOX_H = 50;

    private final BlockPos screen;
    private TextField server;
    private TextButton connect;
    private String serverText = "";
    private Component status = Component.empty();
    private boolean statusIsError;
    private String code;
    private boolean closed;

    public JellyfinLoginScreen(Screen parent, BlockPos screen) {
        super(Component.translatable("service." + BlockCinema.MODID + ".jellyfin"), parent);
        this.screen = screen;
        subtitle = Component.translatable(key("subtitle"));
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + ".jellyfin." + name;
    }

    @Override
    protected int panelWidth() {
        return 320;
    }

    @Override
    protected int panelHeight() {
        return Ui.HEADER + Ui.PAD + 20 + 22 + CODE_BOX_H + 18 + Ui.PAD;
    }

    @Override
    protected void initContent() {
        closed = false;
        int x = contentLeft();
        int w = contentWidth();
        int y = contentTop();
        int buttonW = 84;
        server = addRenderableWidget(new TextField(x, y, w - buttonW - 6, 20, Component.translatable(key("server_hint"))));
        server.setMaxLength(256);
        server.setValue(serverText);
        server.setResponder(text -> serverText = text);
        server.setEditable(code == null);
        setInitialFocus(server);
        connect = addRenderableWidget(new TextButton(x + w - buttonW, y, buttonW, 20, Component.translatable(key("connect")), true, this::connect));
        connect.active = code == null;
    }

    private void setStatus(Component message, boolean error) {
        status = message;
        statusIsError = error;
    }

    private void connect() {
        Optional<String> address = JellyfinApi.normalizeServer(serverText);
        if (address.isEmpty()) {
            setStatus(Component.translatable(key("bad_server")), true);
            return;
        }
        String deviceId = JellyfinLogin.savedDeviceId().orElseGet(() -> UUID.randomUUID().toString());
        JellyfinApi api = new JellyfinApi(address.get(), deviceId, JellyfinApi.appVersion(), null);
        connect.active = false;
        setStatus(Component.translatable(key("connecting")), false);
        api.serverName()
                .thenCompose(name -> api.startQuickConnect())
                .whenComplete((quickConnect, error) -> minecraft.execute(() -> {
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        BlockCinema.LOGGER.warn("Jellyfin Quick Connect failed to start", error);
                        connect.active = true;
                        setStatus(Component.translatable(key("quick_connect_failed")), true);
                        return;
                    }
                    code = quickConnect.code();
                    server.setEditable(false);
                    setStatus(Component.translatable(key("enter_code")), false);
                    poll(api, quickConnect.secret(), Util.getMillis());
                }));
    }

    /** Asks every couple of seconds whether the code was approved. */
    private void poll(JellyfinApi api, String secret, long startedAt) {
        CompletableFuture.runAsync(() -> {
        }, CompletableFuture.delayedExecutor(POLL_SECONDS, TimeUnit.SECONDS))
                .thenCompose(ignored -> api.quickConnectApproved(secret))
                .thenCompose(approved -> approved ? api.finishQuickConnect(secret) : CompletableFuture.completedFuture(null))
                .whenComplete((login, error) -> minecraft.execute(() -> {
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        BlockCinema.LOGGER.warn("Jellyfin Quick Connect failed", error);
                        failed(Component.translatable(key("quick_connect_failed")));
                    } else if (login != null) {
                        login.save();
                        minecraft.setScreen(new JellyfinBrowseScreen(parent, screen, login, null, title));
                    } else if (Util.getMillis() - startedAt > GIVE_UP_MILLIS) {
                        failed(Component.translatable(key("code_expired")));
                    } else {
                        poll(api, secret, startedAt);
                    }
                }));
    }

    private void failed(Component message) {
        code = null;
        server.setEditable(true);
        connect.active = true;
        setStatus(message, true);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = contentLeft();
        int w = contentWidth();
        int y = contentTop() + 20 + 7;
        g.drawString(font, status, x, y, statusIsError ? Ui.DANGER : Ui.TEXT_DIM, false);
        int boxY = y + 15;
        if (code == null) {
            return;
        }
        Ui.box(g, x, boxY, w, CODE_BOX_H, Ui.SURFACE, Ui.ACCENT);
        // The code, big and spaced out so it's easy to read off.
        String spaced = String.join(" ", code.split(""));
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, boxY + CODE_BOX_H / 2f - 10, 0);
        g.pose().scale(2.5f, 2.5f, 1);
        g.drawString(font, spaced, -font.width(spaced) / 2, 0, Ui.TEXT, false);
        g.pose().popPose();
        Component help = Component.translatable(key("code_help"));
        g.drawString(font, help, x + (w - font.width(help)) / 2, boxY + CODE_BOX_H + 5, Ui.TEXT_DIM, false);
    }

    @Override
    public void removed() {
        closed = true;
    }
}
