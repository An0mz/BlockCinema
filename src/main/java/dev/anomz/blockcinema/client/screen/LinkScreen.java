package dev.anomz.blockcinema.client.screen;

import java.util.Optional;
import java.util.function.Function;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.client.TvRemote;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Asks for a link (or ID) for one service and plays it on the screen. */
public final class LinkScreen extends Screen {
    private static final int WIDTH = 300;

    private final Screen parent;
    private final BlockPos screen;
    private final TvService service;
    private final Function<String, Optional<String>> toPage;
    private EditBox link;
    private Component status = Component.empty();

    public LinkScreen(Screen parent, BlockPos screen, TvService service, Function<String, Optional<String>> toPage) {
        super(Component.translatable(service.translationKey()));
        this.parent = parent;
        this.screen = screen;
        this.service = service;
        this.toPage = toPage;
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2;
        int top = height / 2 - 30;
        link = new EditBox(font, left, top, WIDTH, 20, Component.translatable(hintKey()));
        link.setMaxLength(256);
        link.setHint(Component.translatable(hintKey()));
        addRenderableWidget(link);
        setInitialFocus(link);
        int half = (WIDTH - 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("screen." + BlockCinema.MODID + ".play"), button -> play())
                .bounds(left, top + 28, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(left + WIDTH - half, top + 28, half, 20).build());
    }

    private String hintKey() {
        return "screen." + BlockCinema.MODID + ".link_hint." + service.id();
    }

    private void play() {
        Optional<String> page = toPage.apply(link.getValue());
        if (page.isEmpty()) {
            status = Component.translatable("screen." + BlockCinema.MODID + ".bad_link." + service.id());
            return;
        }
        TvRemote.play(screen, service, page.get());
        minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Enter plays, like the Play button.
        if ((keyCode == 257 || keyCode == 335) && link.isFocused()) {
            play();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        graphics.drawCenteredString(font, status, width / 2, height / 2 + 26, 0xFF5555);
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
