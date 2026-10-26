package com.revivalcampfire;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReviveMenu extends AbstractContainerMenu {
    public record Entry(UUID id, String name) {}

    public final BlockPos pos;
    public final List<Entry> banned;

    /** Cliente */
    public ReviveMenu(int windowId, Inventory inv, FriendlyByteBuf buf) {
        super(RevivalCampfireMod.REVIVE_MENU.get(), windowId);
        this.pos = buf.readBlockPos();
        int n = buf.readVarInt();
        this.banned = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            banned.add(new Entry(buf.readUUID(), buf.readUtf()));
        }
    }

    /** Servidor */
    public ReviveMenu(int windowId, BlockPos pos, List<Entry> banned) {
        super(RevivalCampfireMod.REVIVE_MENU.get(), windowId);
        this.pos = pos;
        this.banned = banned;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(RevivalCampfireMod.CAMPFIRE.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
