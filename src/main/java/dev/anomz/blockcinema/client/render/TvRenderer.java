package dev.anomz.blockcinema.client.render;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.anomz.blockcinema.block.TvBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Draws the TV's picture on its front face, letterboxed inside the bezel. For now always the test pattern. */
public final class TvRenderer implements BlockEntityRenderer<TvBlockEntity> {
    /** Frame around the screen, matching the bezel in the TV texture. */
    private static final float BEZEL = 1 / 16f;
    private static final float FRONT = -0.5f - 0.002f;

    public TvRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TvBlockEntity tv, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        BlockState state = tv.getBlockState();
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return;
        }
        // Picture half-size in blocks, letterboxed into the square inside the bezel.
        float area = 1 - 2 * BEZEL;
        float aspect = TestPattern.WIDTH / (float) TestPattern.HEIGHT;
        float halfW = Math.min(area, area * aspect) / 2;
        float halfH = halfW / aspect;

        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        // Front face at -Z, the viewer's left is +X.
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(TestPattern.texture()));
        vertex(consumer, pose, halfW, halfH, 0, 0);
        vertex(consumer, pose, halfW, -halfH, 0, 1);
        vertex(consumer, pose, -halfW, -halfH, 1, 1);
        vertex(consumer, pose, -halfW, halfH, 1, 0);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float u, float v) {
        consumer.addVertex(pose, x, y, FRONT).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }
}
