package dev.anomz.blockcinema.client.screen.ui;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.anomz.blockcinema.BlockCinema;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** The look of Block Cinema's screens: a dark media-player style with one violet accent. */
public final class Ui {
    public static final int PANEL = 0xF2141519;
    public static final int PANEL_BORDER = 0xFF2A2D36;
    public static final int DIVIDER = 0xFF23252D;
    public static final int SURFACE = 0xFF1D1F26;
    public static final int SURFACE_HOVER = 0xFF272A34;
    public static final int TRACK = 0xFF343743;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int ACCENT_HOVER = 0xFFA27DFF;
    public static final int ACCENT_SOFT = 0x408B5CF6;
    public static final int TEXT = 0xFFEDEDF2;
    public static final int TEXT_DIM = 0xFF9A9CAA;
    public static final int TEXT_FAINT = 0xFF5E606C;
    public static final int DANGER = 0xFFEF5D60;

    public static final int PAD = 10;
    public static final int HEADER = 36;

    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "textures/gui/icons.png");
    private static final int ICON_SOURCE = 32;
    private static final int ATLAS_COLUMNS = 8;

    private Ui() {
    }

    /** A rectangle with its corners cut by two pixels, which reads as rounded at GUI scale. */
    public static void round(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 4 || h < 4) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 2, y, x + w - 2, y + 1, color);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, color);
        g.fill(x, y + 2, x + w, y + h - 2, color);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
    }

    /** A rounded box with a one-pixel border. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        round(g, x, y, w, h, border);
        round(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    /** The screen's main panel, with a soft shadow. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x - 2, y - 1, w + 4, h + 4, 0x40000000);
        box(g, x, y, w, h, PANEL, PANEL_BORDER);
    }

    /** One of the {@link Icon}s, {@code size} GUI pixels square, tinted {@code color} (ARGB). */
    public static void icon(GuiGraphics g, Icon icon, int x, int y, int size, int color) {
        int index = icon.ordinal();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, (color >>> 24) / 255f);
        g.blit(ICONS, x, y, size, size, index % ATLAS_COLUMNS * ICON_SOURCE, index / ATLAS_COLUMNS * ICON_SOURCE,
                ICON_SOURCE, ICON_SOURCE, ATLAS_COLUMNS * ICON_SOURCE, 2 * ICON_SOURCE);
        g.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }
}
