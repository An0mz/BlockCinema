package dev.anomz.blockcinema.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.anomz.blockcinema.BlockCinema;
import dev.anomz.blockcinema.TvService;
import dev.anomz.blockcinema.TvSource;
import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
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
        BlockState state = tv.getBlockState();
        if (tv.getLevel() == null || !state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        TvLayout layout = tv.layout(tv.getLevel().getGameTime());
        if (layout.invalid()) {
            return;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (inFront(tv, facing, camera)) {
            TvPlayers.markSeen(layout.origin());
        }
        // The same gap for every TV of the screen (measured to its origin), so the picture stays flat.
        float gap = Math.clamp((float) Math.sqrt(layout.origin().distToCenterSqr(camera)) * GAP_PER_DISTANCE,
                MIN_GAP, MAX_GAP);
        float front = -0.5f - gap;

        // Picture rectangle in screen coordinates (blocks; x from the viewer's left, y up), letterboxed.
        float areaW = layout.cols() - 2 * BEZEL;
        float areaH = layout.rows() - 2 * BEZEL;
        // Browsers and the test pattern are both 16:9.
        float aspect = 16 / 9f;
        float picW = Math.min(areaW, areaH * aspect);
        float picH = picW / aspect;
        float picX0 = (layout.cols() - picW) / 2;
        float picY0 = (layout.rows() - picH) / 2;

        // The part of the picture over this TV's cell.
        float x0 = Math.max(picX0, layout.col());
        float x1 = Math.min(picX0 + picW, layout.col() + 1);
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
        // Front face at -Z, the viewer's left is +X.
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        Matrix4f pose = poseStack.last().pose();
        ResourceLocation picture = TvPlayers.texture(layout.origin());
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(picture != null ? picture : TestPattern.texture()));
        float left = 0.5f - (x0 - layout.col());
        float right = 0.5f - (x1 - layout.col());
        float top = y1 - layout.row() - 0.5f;
        float bottom = y0 - layout.row() - 0.5f;
        vertex(consumer, pose, left, top, front, u0, vTop);
        vertex(consumer, pose, left, bottom, front, u0, vBottom);
        vertex(consumer, pose, right, bottom, front, u1, vBottom);
        vertex(consumer, pose, right, top, front, u1, vTop);
        if (picture == null && layout.col() == 0 && layout.row() == 0) {
            drawCard(tv, layout, poseStack, buffers, picW, front);
        }
        poseStack.popPose();
    }

    /**
     * Someone else's Jellyfin plays only for them, so the screen says who is watching. Drawn once, by
     * the screen's origin TV (this one, bottom-left), centered on the whole screen.
     */
    private static void drawCard(TvBlockEntity origin, TvLayout layout, PoseStack poseStack, MultiBufferSource buffers,
            float picW, float front) {
        TvSource source = origin.source();
        if (source == null || source.service() != TvService.JELLYFIN || TvPlayers.isPlaying(origin.getBlockPos())) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable("screen." + BlockCinema.MODID + ".jellyfin_card", source.ownerName());
        float scale = Math.min(picW * 0.8f / Math.max(1, font.width(text)), 1 / 32f);
        poseStack.pushPose();
        // The middle of the screen, as seen from the origin cell; x runs to the viewer's left here.
        poseStack.translate(0.5f - layout.cols() / 2f, layout.rows() / 2f - 0.5f, front - 0.005f);
        // Font space has x to the right and y down: turn it to face the viewer.
        poseStack.scale(-scale, -scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight / 2f, 0xFFFFFFFF, false,
                poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0xC0000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    /** Whether the camera is on the screen side of this TV's front face. */
    private static boolean inFront(TvBlockEntity tv, Direction facing, Vec3 camera) {
        Vec3 toCamera = camera.subtract(Vec3.atCenterOf(tv.getBlockPos()));
        return toCamera.x * facing.getStepX() + toCamera.z * facing.getStepZ() > 0.5;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, float u, float v) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }
}
