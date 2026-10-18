package dev.anomz.blockcinema.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.anomz.blockcinema.block.TvBlockEntity;
import dev.anomz.blockcinema.block.TvLayout;
import dev.anomz.blockcinema.client.browser.TvPlayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws this TV's slice of its screen's picture, letterboxed inside the bezel around the whole screen.
 * Every TV of a screen computes the same picture rectangle and draws only the part over itself.
 * The picture is the screen's browser, or the test pattern while nothing is playing.
 */
public final class TvRenderer implements BlockEntityRenderer<TvBlockEntity> {
    /** Frame around the whole screen, matching the bezel in the TV texture. */
    private static final float BEZEL = 1 / 16f;
    private static final float FRONT = -0.5f - 0.002f;

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

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
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
        vertex(consumer, pose, left, top, u0, vTop);
        vertex(consumer, pose, left, bottom, u0, vBottom);
        vertex(consumer, pose, right, bottom, u1, vBottom);
        vertex(consumer, pose, right, top, u1, vTop);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, FRONT).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }
}
