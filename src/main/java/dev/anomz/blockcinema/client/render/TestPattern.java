package dev.anomz.blockcinema.client.render;

import com.mojang.blaze3d.platform.NativeImage;

import dev.anomz.blockcinema.BlockCinema;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** A 16:9 colour-bar test pattern, shown until the TV has something to play. Made once, on first use. */
public final class TestPattern {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 180;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(BlockCinema.MODID, "test_pattern");

    // 75% colour bars, then the reversed strip and the dark bottom strip (0xRRGGBB).
    private static final int[] TOP_BARS = { 0xBFBFBF, 0xBFBF00, 0x00BFBF, 0x00BF00, 0xBF00BF, 0xBF0000, 0x0000BF };
    private static final int[] MIDDLE_BARS = { 0x0000BF, 0x131313, 0xBF00BF, 0x131313, 0x00BFBF, 0x131313, 0xBFBFBF };
    private static final int[] BOTTOM_BARS = { 0x00214C, 0xFFFFFF, 0x32006A, 0x131313, 0x090909, 0x131313, 0x1D1D1D };

    private static ResourceLocation texture;

    private TestPattern() {
    }

    /** The pattern's texture, made on the render thread the first time it is needed. */
    public static ResourceLocation texture() {
        if (texture == null) {
            Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(draw()));
            texture = ID;
        }
        return texture;
    }

    private static NativeImage draw() {
        NativeImage image = new NativeImage(WIDTH, HEIGHT, false);
        int topEnd = HEIGHT * 2 / 3;
        int middleEnd = HEIGHT * 3 / 4;
        for (int y = 0; y < HEIGHT; y++) {
            int[] bars = y < topEnd ? TOP_BARS : y < middleEnd ? MIDDLE_BARS : BOTTOM_BARS;
            for (int x = 0; x < WIDTH; x++) {
                image.setPixelRGBA(x, y, abgr(bars[x * bars.length / WIDTH]));
            }
        }
        return image;
    }

    /** NativeImage stores pixels as ABGR. */
    private static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF);
    }
}
