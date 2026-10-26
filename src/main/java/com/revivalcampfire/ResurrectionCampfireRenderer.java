package com.revivalcampfire;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

public class ResurrectionCampfireRenderer implements BlockEntityRenderer<ResurrectionCampfireBlockEntity> {
    @Override
    public void render(ResurrectionCampfireBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buf,
                       int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        FogataModel.render(ps, buf, light, overlay);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ResurrectionCampfireBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
