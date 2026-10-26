package com.revivalcampfire;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Dibuja la fogata como objeto (inventario, mano, suelo). */
public class CampfireItemRenderer extends BlockEntityWithoutLevelRenderer {
    public CampfireItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.05, 0.5);
        float s = 0.17f;
        ps.scale(s, s, s);
        FogataModel.render(ps, buf, light, overlay);
        ps.popPose();
    }
}
