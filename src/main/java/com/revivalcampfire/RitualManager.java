package com.revivalcampfire;

import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Logica del ritual en el servidor. El audio, el avatar y todos los efectos visuales los pone el cliente
 * (ClientRitual / RitualRenderer) a partir del paquete StartRitual; aqui solo vive lo "real":
 * el desbaneo, el anuncio y el punto donde reaparece el jugador (donde cae su avatar).
 *
 * Linea de tiempo (20 ticks = 1 s):  0 inicio ... 571 destello ... 573 desbaneo ... 663 el avatar aterriza ... 735 fin.
 */
public class RitualManager {
    public static final int TOTAL = 735;
    public static final int UNBAN_TICK = 573;
    public static final int STAND_SPAWN = 563;
    public static final int STAND_LAND = 663;
    public static final double DESCEND_HEIGHT = 40.0;
    private static final Vec3 LAND_OFFSET = new Vec3(-7.0, 0, 4.0);

    private static final List<Ritual> ACTIVE = new ArrayList<>();

    /** Posicion (pies) del avatar segun la edad del ritual. Se usa tambien en el cliente. */
    public static Vec3 figurePos(Vec3 c, float age) {
        float k = Mth.clamp((age - STAND_SPAWN) / (float) (STAND_LAND - STAND_SPAWN), 0f, 1f);
        float e = 1f - (1f - k) * (1f - k) * (1f - k);
        return new Vec3(c.x + LAND_OFFSET.x, c.y - 0.5 + DESCEND_HEIGHT * (1f - e), c.z + LAND_OFFSET.z);
    }

    // ------------------------------------------------------------------ baneados
    private static List<GameProfile> bannedProfiles(MinecraftServer server) {
        UserBanList bans = server.getPlayerList().getBans();
        List<GameProfile> out = new ArrayList<>();
        for (String key : bans.getUserList()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            UserBanListEntry entry = bans.get(new GameProfile(id, null));
            if (entry == null) continue;
            out.add(new GameProfile(id, entry.getDisplayName().getString()));
        }
        return out;
    }

    public static List<ReviveMenu.Entry> banned(MinecraftServer server) {
        List<ReviveMenu.Entry> out = new ArrayList<>();
        for (GameProfile p : bannedProfiles(server)) out.add(new ReviveMenu.Entry(p.getId(), p.getName()));
        out.sort(Comparator.comparing(x -> x.name().toLowerCase()));
        return out;
    }

    private static GameProfile findBanned(MinecraftServer server, UUID id) {
        for (GameProfile p : bannedProfiles(server)) if (id.equals(p.getId())) return p;
        return null;
    }

    public static boolean canUse(ServerPlayer p) {
        return p.server.isSingleplayer() || p.hasPermissions(Config.REQUIRED_PERMISSION.get());
    }

    private static GameProfile fakeProfile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }

    public static void addTestBan(MinecraftServer server, String name) {
        UserBanList bans = server.getPlayerList().getBans();
        bans.add(new UserBanListEntry(fakeProfile(name), new Date(), "RevivalCampfire", null, "Baneo de prueba"));
        try {
            bans.save();
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------------ inicio
    private static Component begin(ServerLevel level, Vec3 center, GameProfile profile, String reviver, UUID reviverId) {
        for (Ritual r : ACTIVE) {
            if (r.profile.getId().equals(profile.getId())) {
                return Component.translatable("msg.revivalcampfire.in_progress", profile.getName());
            }
        }
        ACTIVE.add(new Ritual(level, center, profile, reviver, reviverId == null ? new UUID(0L, 0L) : reviverId));
        return null;
    }

    /** @return null si todo va bien, o el mensaje de error. */
    public static Component start(ServerLevel level, Vec3 center, UUID id, String reviver, UUID reviverId) {
        GameProfile profile = findBanned(level.getServer(), id);
        if (profile == null) return Component.translatable("msg.revivalcampfire.not_banned", id.toString());
        return begin(level, center, profile, reviver, reviverId);
    }

    /** Ritual de demostracion con un jugador ficticio. */
    public static Component startDemo(ServerLevel level, Vec3 center, String name, String reviver, UUID reviverId) {
        return begin(level, center, fakeProfile(name), reviver, reviverId);
    }

    public static void tick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || ACTIVE.isEmpty()) return;
        Iterator<Ritual> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Ritual r = it.next();
            r.age++;
            if (!r.step(server)) it.remove();
        }
    }

    public static void shutdown() {
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ ritual
    private static class Ritual {
        final ServerLevel level;
        final Vec3 center;
        final GameProfile profile;
        final String reviver;
        final UUID reviverId;
        int age = 0;

        Ritual(ServerLevel level, Vec3 center, GameProfile profile, String reviver, UUID reviverId) {
            this.level = level;
            this.center = center;
            this.profile = profile;
            this.reviver = reviver;
            this.reviverId = reviverId;
        }

        boolean step(MinecraftServer server) {
            if (age == 1) {
                Network.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                                new PacketDistributor.TargetPoint(center.x, center.y, center.z, 160, level.dimension())),
                        new Network.StartRitual(center.x, center.y, center.z, profile.getId(), profile.getName(), reviverId));
            }
            if (age == UNBAN_TICK) {
                UserBanList bans = server.getPlayerList().getBans();
                bans.remove(profile);
                try {
                    bans.save();
                } catch (IOException ignored) {
                }
                // el jugador reaparecera donde cae su avatar
                ReviveData.get(server).put(profile.getId(), level, figurePos(center, STAND_LAND));
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("msg.revivalcampfire.broadcast", reviver, profile.getName())
                                .withStyle(ChatFormatting.AQUA), false);
            }
            return age < TOTAL;
        }
    }
}
