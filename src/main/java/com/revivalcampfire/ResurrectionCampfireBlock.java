package com.revivalcampfire;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

public class ResurrectionCampfireBlock extends BaseEntityBlock {
    private static final VoxelShape SELECT = Block.box(0, 0, 0, 16, 32, 16);
    private static final VoxelShape COLLIDE = Block.box(0, 0, 0, 16, 7, 16);

    public ResurrectionCampfireBlock(Properties props) {
        super(props);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResurrectionCampfireBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SELECT;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return COLLIDE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            if (!RitualManager.canUse(sp)) {
                sp.displayClientMessage(Component.translatable("msg.revivalcampfire.no_permission"), true);
                return InteractionResult.CONSUME;
            }
            if (!(sp.getMainHandItem().getItem() instanceof SpoonItem)) {
                sp.displayClientMessage(Component.translatable("msg.revivalcampfire.need_spoon"), true);
                return InteractionResult.CONSUME;
            }
            List<ReviveMenu.Entry> banned = RitualManager.banned(sp.server);
            NetworkHooks.openScreen(sp,
                    new SimpleMenuProvider((id, inv, p) -> new ReviveMenu(id, pos, banned),
                            Component.translatable("gui.revivalcampfire.title")),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeVarInt(banned.size());
                        for (ReviveMenu.Entry e : banned) {
                            buf.writeUUID(e.id());
                            buf.writeUtf(e.name());
                        }
                    });
        }
        return InteractionResult.CONSUME;
    }

    /** Humo y crepitar como una fogata normal. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        if (rand.nextInt(10) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5f + rand.nextFloat(), rand.nextFloat() * 0.7f + 0.6f, false);
        }
        if (rand.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.LAVA, x + (rand.nextDouble() - 0.5) * 0.4, y, z + (rand.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
        if (rand.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x + rand.nextDouble() * 0.4 - 0.2, y + 3.2, z + rand.nextDouble() * 0.4 - 0.2,
                    (rand.nextDouble() / 100.0) * (rand.nextBoolean() ? 1 : -1), 0.07,
                    (rand.nextDouble() / 100.0) * (rand.nextBoolean() ? 1 : -1));
        }
    }
}
