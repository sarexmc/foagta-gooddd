package com.revivalcampfire;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Solo existe para que la fogata (modelo grande) se dibuje con un renderizador propio. */
public class ResurrectionCampfireBlockEntity extends BlockEntity {
    public ResurrectionCampfireBlockEntity(BlockPos pos, BlockState state) {
        super(RevivalCampfireMod.CAMPFIRE_BE.get(), pos, state);
    }

    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = getBlockPos();
        return new AABB(p.getX() - 4, p.getY() - 1, p.getZ() - 4, p.getX() + 5, p.getY() + 6, p.getZ() + 5);
    }
}
