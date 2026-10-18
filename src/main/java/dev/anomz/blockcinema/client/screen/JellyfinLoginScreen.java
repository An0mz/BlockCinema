package dev.anomz.blockcinema.client.screen;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.jellyfin.JellyfinApi;
import dev.anomz.blockcinema.client.jellyfin.JellyfinLogin;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Logs in to the player's Jellyfin server with Quick Connect: the screen shows a code, the player
 * approves it in any other Jellyfin app, and no password is ever typed into Minecraft.
 */
public final class JellyfinLoginScreen extends Screen {
    private static final int WIDTH = 300;
    private static final long POLL_SECONDS = 2;
    /** Jellyfin forgets Quick Connect codes after a while; stop asking well before that. */
    private static final long GIVE_UP_MILLIS = 5 * 60 * 1000;

    private final Screen parent;
    private final BlockPos screen;
    private EditBox server;
    private Button connect;
    private String serverText = "";
    private Component status = Component.empty();
    private String code;
    private boolean closed;

    public JellyfinLoginScreen(Screen parent, BlockPos screen) {
        super(Component.translatable("service." + BlockCinema.MODID + ".jellyfin"));
        this.parent = parent;
        this.screen = screen;
    }

    @Override
    protected void init() {
        closed = false;
        int left = (width - WIDTH) / 2;
        int top = height / 2 - 30;
        server = new EditBox(font, left, top, WIDTH, 20, Component.translatable(key("server_hint")));
        server.setMaxLength(256);
        server.setHint(Component.translatable(key("server_hint")));
        server.setValue(serverText);
        server.setResponder(text -> serverText = text);
        server.setEditable(code == null);
        addRenderableWidget(server);
        setInitialFocus(server);
        int half = (WIDTH - 8) / 2;
        connect = addRenderableWidget(Button.builder(Component.translatable(key("connect")), button -> connect())
                .bounds(left, top + 28, half, 20).build());
        connect.active = code == null;
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + WIDTH - half, top + 28, half, 20).build());
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + ".jellyfin." + name;
    }

    private void connect() {
        Optional<String> address = JellyfinApi.normalizeServer(serverText);
        if (address.isEmpty()) {
            status = Component.translatable(key("bad_server")).withStyle(ChatFormatting.RED);
            return;
        }
        String deviceId = JellyfinLogin.savedDeviceId().orElseGet(() -> UUID.randomUUID().toString());
        JellyfinApi api = new JellyfinApi(address.get(), deviceId, JellyfinApi.appVersion(), null);
        connect.active = false;
        status = Component.translatable(key("connecting"));
        api.serverName()
                .thenCompose(name -> api.startQuickConnect())
                .whenComplete((quickConnect, error) -> minecraft.execute(() -> {
                    if (closed) {
                        return;
                    }
                    if (error != null) {
                        BlockCinema.LOGGER.warn("Jellyfin Quick Connect failed to start", error);
                        connect.active = true;
                        status = Component.translatable(key("quick_connect_failed")).withStyle(ChatFormatting.RED);
                        return;
                    }
                    code = quickConnect.code();
                    server.setEditable(false);
                    status = Component.translatable(key("enter_code"));
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
        status = message.copy().withStyle(ChatFormatting.RED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, height / 2 + 26, 0xAAAAAA);
        if (code != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2f, height / 2f + 44, 0);
            graphics.pose().scale(3, 3, 1);
            graphics.drawCenteredString(font, code, 0, 0, 0xFFFFFF);
            graphics.pose().popPose();
            graphics.drawCenteredString(font, Component.translatable(key("code_help")), width / 2, height / 2 + 80, 0xAAAAAA);
        }
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
}
