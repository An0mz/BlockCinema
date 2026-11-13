package dev.anomz.blockcinema.client.screen;

import java.util.Optional;
import java.util.function.Function;

import org.lwjgl.glfw.GLFW;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvServerConfig;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.Agreements;
import dev.anomz.blockcinema.client.TvRemote;
import dev.anomz.blockcinema.client.browser.VideoLinks;
import dev.anomz.blockcinema.client.screen.ui.PanelScreen;
import dev.anomz.blockcinema.client.screen.ui.TextButton;
import dev.anomz.blockcinema.client.screen.ui.TextField;
import dev.anomz.blockcinema.client.screen.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks for a link (or ID) for one service and plays it on the screen. */
public final class LinkScreen extends PanelScreen {
    private static final int FILES_ROW = 26;

    private final BlockPos screen;
    private final TvService service;
    private final Function<String, Optional<String>> toPage;
    private TextField link;
    private String linkText = "";
    private Component error = Component.empty();

    public LinkScreen(Screen parent, BlockPos screen, TvService service, Function<String, Optional<String>> toPage) {
        super(Component.translatable(service.translationKey()), parent);
        this.screen = screen;
        this.service = service;
        this.toPage = toPage;
        subtitle = Component.translatable(key("tile." + service.id()));
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + "." + name;
    }

    @Override
    protected int panelWidth() {
        return 320;
    }

    @Override
    protected int panelHeight() {
        return Ui.HEADER + Ui.PAD + 20 + 22 + 20 + Ui.PAD + 4 + (offersFiles() ? FILES_ROW : 0);
    }

    /** A video link can also be a file of the player's own, where the server allows those. */
    private boolean offersFiles() {
        return service == TvService.DIRECT_LINK && TvServerConfig.localFiles();
    }

    @Override
    protected void initContent() {
        int x = contentLeft();
        int w = contentWidth();
        int y = contentTop();
        link = addRenderableWidget(new TextField(x, y, w, 20, Component.translatable(key("link_hint." + service.id()))));
        link.setMaxLength(2048);
        link.setValue(linkText);
        link.setResponder(text -> {
            linkText = text;
            error = Component.empty();
        });
        setInitialFocus(link);
        int buttonY = y + 20 + 22;
        addRenderableWidget(new TextButton(x + w - 80, buttonY, 80, 20, Component.translatable(key("play")), true, this::play));
        int cancelX = x + w - 80 - 6 - 70;
        if (TvRemote.source(screen) != null) {
            int queueW = 90;
            addRenderableWidget(new TextButton(x + w - 80 - 6 - queueW, buttonY, queueW, 20, Component.translatable(key("add_to_queue")),
                    false, this::enqueue));
            cancelX = x;
        }
        addRenderableWidget(new TextButton(cancelX, buttonY, 70, 20, CommonComponents.GUI_CANCEL, false, this::onClose));
        if (offersFiles()) {
            Component files = Component.translatable(key("my_files"));
            addRenderableWidget(new TextButton(x, buttonY + FILES_ROW, font.width(files) + 16, 20, files, false,
                    () -> minecraft.setScreen(new LocalFilesScreen(this, screen))));
        }
    }

    private void play() {
        start(false);
    }

    private void enqueue() {
        start(true);
    }

    private void start(boolean queued) {
        // A YouTube or Twitch link pasted as a video link is a web page, not a file: it goes to that service's player.
        TvService site = service == TvService.DIRECT_LINK ? VideoLinks.siteOf(linkText) : null;
        TvService target = site == null ? service : site;
        if (!TvServerConfig.enabled(target)) {
            error = Component.translatable(key("other_service_off"), Component.translatable(target.translationKey()));
            return;
        }
        String format = target == TvService.DIRECT_LINK ? VideoLinks.unplayableFormat(linkText) : null;
        Optional<String> found = format != null ? Optional.empty()
                : site == TvService.YOUTUBE ? VideoLinks.youtube(linkText)
                : site == TvService.TWITCH ? VideoLinks.twitch(linkText)
                : toPage.apply(linkText);
        if (found.isEmpty()) {
            error = format == null ? Component.translatable(key("bad_link." + target.id()))
                    : Component.translatable(key("bad_format"), format);
            return;
        }
        String page = found.get();
        Runnable send = () -> {
            if (queued) {
                TvRemote.enqueue(screen, target, page, VideoLinks.label(page));
            } else {
                TvRemote.play(screen, target, page);
            }
            minecraft.setScreen(null);
        };
        if (target == TvService.YOUTUBE && !Agreements.youtube()) {
            minecraft.setScreen(new AgreementScreen(this, send));
        } else {
            send.run();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && link.isFocused()) {
            play();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, error, contentLeft(), contentTop() + 20 + 7, Ui.DANGER, false);
    }
}
