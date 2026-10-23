package dev.anomz.blockcinema.client.screen;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.client.Agreements;
import dev.anomz.blockcinema.client.screen.ui.PanelScreen;
import dev.anomz.blockcinema.client.screen.ui.TextButton;
import dev.anomz.blockcinema.client.screen.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * YouTube's developer policies ask players to agree to the YouTube Terms of Service and a privacy policy
 * that links the Google Privacy Policy before YouTube plays. This screen explains it, links all of them,
 * and records the answer (see {@link Agreements}); it also lets the player withdraw it later.
 */
public final class AgreementScreen extends PanelScreen {
    private static final int LINE_H = 10;

    @Nullable
    private final Runnable onAgree;
    private List<FormattedCharSequence> lines = List.of();

    /** {@code onAgree} runs once the player agrees (null: just close). */
    public AgreementScreen(@Nullable Screen parent, @Nullable Runnable onAgree) {
        super(Component.translatable(key("title")), parent);
        this.onAgree = onAgree;
        subtitle = Component.translatable(key(Agreements.youtube() ? "subtitle_agreed" : "subtitle"));
    }

    private static String key(String name) {
        return "screen." + BlockCinema.MODID + ".agreement." + name;
    }

    @Override
    protected int panelWidth() {
        return 340;
    }

    @Override
    protected int panelHeight() {
        lines = font.split(Component.translatable(key("body")), Math.min(340, width - 16) - 2 * (Ui.PAD + 2));
        return Ui.HEADER + Ui.PAD + lines.size() * LINE_H + 10 + 2 * 20 + 4 + 14 + 20 + Ui.PAD;
    }

    @Override
    protected void initContent() {
        int x = contentLeft();
        int w = contentWidth();
        int y = contentTop() + lines.size() * LINE_H + 10;
        int half = (w - 6) / 2;
        link(x, y, half, "youtube_terms", Agreements.YOUTUBE_TERMS);
        link(x + w - half, y, half, "google_privacy", Agreements.GOOGLE_PRIVACY);
        link(x, y + 24, half, "privacy", Agreements.PRIVACY_POLICY);
        link(x + w - half, y + 24, half, "terms", Agreements.TERMS);
        int buttonY = y + 2 * 20 + 4 + 14;
        if (Agreements.youtube()) {
            addRenderableWidget(new TextButton(x + w - 90, buttonY, 90, 20, Component.translatable(key("close")), true, this::onClose));
            addRenderableWidget(new TextButton(x + w - 90 - 6 - 90, buttonY, 90, 20, Component.translatable(key("withdraw")), false, () -> {
                Agreements.setYoutube(false);
                onClose();
            }));
        } else {
            addRenderableWidget(new TextButton(x + w - 90, buttonY, 90, 20, Component.translatable(key("agree")), true, () -> {
                Agreements.setYoutube(true);
                if (onAgree != null) {
                    onAgree.run();
                } else {
                    onClose();
                }
            }));
            addRenderableWidget(new TextButton(x + w - 90 - 6 - 90, buttonY, 90, 20, Component.translatable(key("not_now")), false, this::onClose));
        }
    }

    private void link(int x, int y, int w, String name, String url) {
        addRenderableWidget(new TextButton(x, y, w, 20, Component.translatable(key(name)), false,
                () -> ConfirmLinkScreen.confirmLinkNow(this, url)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = contentTop();
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, contentLeft(), y, Ui.TEXT_DIM, false);
            y += LINE_H;
        }
    }
}
