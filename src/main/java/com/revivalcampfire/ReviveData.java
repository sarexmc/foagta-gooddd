package com.revivalcampfire;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Donde debe aparecer cada jugador revivido la proxima vez que entre (donde cae su avatar). */
public class ReviveData extends SavedData {
    private record Spot(String dim, double x, double y, double z) {}

    private final Map<UUID, Spot> spots = new HashMap<>();

    public static ReviveData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ReviveData::load, ReviveData::new, "revivalcampfire_revive");
    }

    public void put(UUID id, ServerLevel level, Vec3 pos) {
        spots.put(id, new Spot(level.dimension().location().toString(), pos.x, pos.y, pos.z));
        setDirty();
    }

    public void teleportIfPending(ServerPlayer p) {
        Spot s = spots.remove(p.getUUID());
        if (s == null) return;
        setDirty();
        ServerLevel lvl = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(s.dim())));
        if (lvl != null) p.teleportTo(lvl, s.x(), s.y(), s.z(), p.getYRot(), p.getXRot());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Spot> e : spots.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("id", e.getKey());
            c.putString("dim", e.getValue().dim());
            c.putDouble("x", e.getValue().x());
            c.putDouble("y", e.getValue().y());
            c.putDouble("z", e.getValue().z());
            list.add(c);
        }
        tag.put("spots", list);
        return tag;
    }

    public static ReviveData load(CompoundTag tag) {
        ReviveData d = new ReviveData();
        ListTag list = tag.getList("spots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            d.spots.put(c.getUUID("id"), new Spot(c.getString("dim"), c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
        return d;
    }
}
