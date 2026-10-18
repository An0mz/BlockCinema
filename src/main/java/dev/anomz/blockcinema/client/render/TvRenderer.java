package dev.anomz.blockcinema.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvPageRules;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.ScreenFrame;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import dev.anomz.blockcinema.client.jellyfin.JellyfinWatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Draws this TV's slice of its screen's picture, letterboxed inside the bezel around the whole screen.
 * Every TV of a screen computes the same picture rectangle and draws only the part over itself.
 * The picture is the screen's browser, or the test pattern while nothing is playing.
 */
public final class TvRenderer implements BlockEntityRenderer<TvBlockEntity> {
    /** Frame around the whole screen, matching the bezel in the TV texture. */
    private static final float BEZEL = 1 / 16f;
    /**
     * How far the picture floats in front of the TV face, in blocks per block of distance to the camera.
     * Depth precision drops with distance, so a fixed gap lets the dark face show through far away
     * (z-fighting); growing the gap with distance keeps it too small to notice at any range.
     */
    private static final float GAP_PER_DISTANCE = 0.0006f;
    private static final float MIN_GAP = 0.002f;
    private static final float MAX_GAP = 0.06f;

    public TvRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TvBlockEntity tv, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        if (tv.getLevel() == null) {
            return;
        }
        TvLayout layout = tv.layout(tv.getLevel().getGameTime());
        if (layout.invalid()) {
            return;
        }
        ScreenFrame frame = layout.frame();
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (inFront(tv, frame, camera)) {
            TvPlayers.markSeen(layout.origin());
        }
        // The same gap for every TV of the screen (measured to its origin), so the picture stays flat.
        float gap = Math.clamp((float) Math.sqrt(layout.origin().distToCenterSqr(camera)) * GAP_PER_DISTANCE,
                MIN_GAP, MAX_GAP);
        float front = (float) -frame.frontDistance() - gap;
        // One TV is a cell this wide (a diagonal TV is wider than a block).
        float cell = (float) frame.cellWidth();

        // Picture rectangle in screen coordinates (blocks; x from the viewer's left, y up), letterboxed.
        float screenW = layout.cols() * cell;
        float areaW = screenW - 2 * BEZEL;
        float areaH = layout.rows() - 2 * BEZEL;
        // Browsers and the test pattern are both 16:9.
        float aspect = 16 / 9f;
        float picW = Math.min(areaW, areaH * aspect);
        float picH = picW / aspect;
        float picX0 = (screenW - picW) / 2;
        float picY0 = (layout.rows() - picH) / 2;

        // The part of the picture over this TV's cell.
        float cellX0 = layout.col() * cell;
        float x0 = Math.max(picX0, cellX0);
        float x1 = Math.min(picX0 + picW, cellX0 + cell);
        float y0 = Math.max(picY0, layout.row());
        float y1 = Math.min(picY0 + picH, layout.row() + 1);
        if (x0 >= x1 || y0 >= y1) {
            return;
        }
        float u0 = (x0 - picX0) / picW;
        float u1 = (x1 - picX0) / picW;
        float vTop = (picY0 + picH - y1) / picH;
        float vBottom = (picY0 + picH - y0) / picH;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // Turned like the TV's model (whose screen faces north): then the screen faces -Z, the top of the
        // picture is +Y and the viewer's left is +X.
        poseStack.mulPose(Axis.YP.rotationDegrees(-frame.modelY() + frame.extraYaw()));
        poseStack.mulPose(Axis.XP.rotationDegrees(-frame.modelX()));
        Matrix4f pose = poseStack.last().pose();
        ResourceLocation picture = TvPlayers.texture(layout.origin());
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(picture != null ? picture : TestPattern.texture()));
        float left = cell / 2 - (x0 - cellX0);
        float right = cell / 2 - (x1 - cellX0);
        float top = y1 - layout.row() - 0.5f;
        float bottom = y0 - layout.row() - 0.5f;
        vertex(consumer, pose, left, top, front, u0, vTop);
        vertex(consumer, pose, left, bottom, front, u0, vBottom);
        vertex(consumer, pose, right, bottom, front, u1, vBottom);
        vertex(consumer, pose, right, top, front, u1, vTop);
        if (picture == null && layout.col() == 0 && layout.row() == 0) {
            drawCard(tv, layout, poseStack, buffers, picW, screenW, cell, front);
        }
        poseStack.popPose();
    }

    /**
     * A Jellyfin TV this player can't stream (no login on that server, no access, or still loading) says
     * who is watching and why there is no picture. Drawn once, by the screen's origin TV (this one,
     * bottom-left), centered on the whole screen.
     */
    private static void drawCard(TvBlockEntity origin, TvLayout layout, PoseStack poseStack, MultiBufferSource buffers,
            float picW, float screenW, float cell, float front) {
        TvSource source = origin.source();
        if (source == null || source.service() != TvService.JELLYFIN || TvPlayers.isPlaying(origin.getBlockPos())) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable("screen." + BlockCinema.MODID + ".jellyfin_card", source.ownerName());
        String[] ids = TvPageRules.jellyfinIds(source.page());
        String why = ids == null || !JellyfinWatch.canWatch(ids[0]) ? "jellyfin_card_log_in"
                : JellyfinWatch.failed(source.page()) ? "jellyfin_card_no_access" : "jellyfin_card_loading";
        Component hint = Component.translatable("screen." + BlockCinema.MODID + "." + why);
        int width = Math.max(font.width(text), font.width(hint));
        float scale = Math.min(picW * 0.8f / Math.max(1, width), 1 / 32f);
        poseStack.pushPose();
        // The middle of the screen, as seen from the origin cell; x runs to the viewer's left here.
        poseStack.translate(cell / 2 - screenW / 2, layout.rows() / 2f - 0.5f, front - 0.005f);
        // Font space has x to the right and y down: turn it to face the viewer.
        poseStack.scale(-scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight - 1, 0xFFFFFFFF, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0xC0000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(hint, -font.width(hint) / 2f, 1, 0xFFAAAAAA, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0xC0000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** Whether the camera is on the screen side of this TV's screen. */
    private static boolean inFront(TvBlockEntity tv, ScreenFrame frame, Vec3 camera) {
        return camera.subtract(Vec3.atCenterOf(tv.getBlockPos())).dot(frame.normal()) > frame.frontDistance();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }
}
