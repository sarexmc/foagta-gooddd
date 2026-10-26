package com.revivalcampfire;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.Util;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Estado y linea de tiempo del ritual en el cliente (ticks desde el inicio, 20 = 1 s):
 *   0-160    aparece un anillo con un punto brillante en el cielo; el dia se apaga poco a poco
 *   90-180   un cubo oscuro cae desde el anillo, impacta y crece hasta envolverlo todo
 *   100-182  auroras mientras oscurece
 *   170-571  el mundo desaparece: vacio negro con estrellas, discos turquesa, arcos orbitando
 *   395-571  una estrella dorada crece sobre la fogata
 *   548-633  destello blanco -> vuelve el dia, la figura desciende
 *   ...735   los arcos se apagan
 */
public class ClientRitual {
    public static final int TOTAL = RitualManager.TOTAL;
    public static final int SPACE_START = 170;
    public static final int SPACE_END = 571;

    private static final RandomSource R = RandomSource.create();
    private static final Vector3f YELLOW = new Vector3f(1.00f, 0.90f, 0.10f);
    private static final Vector3f CYAN = new Vector3f(0.20f, 0.65f, 1.00f);

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static CloudStatus savedClouds = null;

    public static class Active {
        public final Vec3 c;
        public final UUID targetId, reviverId;
        public final String targetName;
        public int age = 0;
        public ResourceLocation skin;
        public AvatarPlayer avatar;
        Active(Vec3 c, UUID targetId, String targetName, UUID reviverId) {
            this.c = c;
            this.targetId = targetId;
            this.targetName = targetName;
            this.reviverId = reviverId;
        }
    }

    /** Avatar (jugador real, sin armor stand) que desciende al final. Se crea la primera vez que hace falta. */
    public static AvatarPlayer avatar(Active a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        if (a.avatar == null || a.avatar.level() != mc.level) {
            a.avatar = new AvatarPlayer(mc.level, new GameProfile(a.targetId, a.targetName));
        }
        a.avatar.skin = a.skin;
        return a.avatar;
    }

    // ---- curvas de la linea de tiempo ----
    public static float ss(float a, float b, float x) {
        float t = Mth.clamp((x - a) / (b - a), 0f, 1f);
        return t * t * (3 - 2 * t);
    }
    public static float dusk(float t) { return t < SPACE_END ? ss(0, 160, t) * 0.92f : 0.30f * (1 - ss(574, 664, t)); }
    public static float starFade(float t) { return t < SPACE_END ? ss(60, 160, t) * 0.8f : 0.8f * (1 - ss(574, 654, t)); }
    public static float aurora(float t) { return ss(100, 130, t) * (1 - ss(160, 182, t)); }
    public static float space(float t) { return ss(168, 178, t) * (1 - ss(SPACE_END - 1, SPACE_END, t)); }
    public static float flash(float t) { return t < 548 ? 0f : (t < 571 ? ss(548, 571, t) : 1f - ss(571, 633, t)); }
    /** Anillo con punto brillante en el cielo (aparece al inicio y se apaga cuando cae el cubo). */
    public static float ringFade(float t) { return ss(8, 40, t) * (1 - ss(140, 165, t)); }
    /** Lado del cubo oscuro: cae desde el anillo (90-150), impacta a los 7,5 s y crece hasta envolverlo todo (150-180). */
    public static float cubeSize(float t) {
        if (t < 90 || t >= 185) return 0f;
        if (t < 150) return 0.6f + 0.6f * ss(90, 150, t);
        return 1.2f * (float) Math.pow(110.0, (t - 150) / 30.0);
    }
    /** Altura (mundo) del centro del cubo. */
    public static double cubeY(double cy, float t) {
        double ringY = cy + RING_HEIGHT, ground = cy + 1;
        if (t >= 150) return ground;
        double k = Mth.clamp((t - 90) / 60.0, 0, 1);
        return ringY - (ringY - ground) * k * k;
    }
    public static final double RING_HEIGHT = 40.0;
    /** Altura de la estrella dorada sobre la fogata y cuanto sube antes del destello blanco. */
    public static final double STAR_HEIGHT = 9.0, STAR_RISE = 50.0;
    public static double starY(double cy, float t) { return cy + STAR_HEIGHT + STAR_RISE * ss(480, 571, t); }

    // ------------------------------------------------------------------ ciclo de vida
    public static void start(Vec3 center, UUID targetId, String targetName, UUID reviverId) {
        Active act = new Active(center, targetId, targetName, reviverId);
        ACTIVE.add(act);
        Minecraft mc = Minecraft.getInstance();
        double d = mc.player != null ? mc.player.position().distanceTo(center) : 0;
        float vol = (float) Mth.clamp((90 - d) / 60.0, 0.0, 1.0);
        if (vol > 0.02f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(RevivalCampfireMod.RITUAL_MAIN.get(), 1.0f, vol));
        }
        // skin real del jugador revivido (si hay conexion con los servidores de Mojang; si no, skin por defecto)
        Util.backgroundExecutor().execute(() -> {
            try {
                GameProfile filled = mc.getMinecraftSessionService().fillProfileProperties(new GameProfile(targetId, targetName), true);
                mc.execute(() -> mc.getSkinManager().registerSkins(filled, (type, loc, tex) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) act.skin = loc;
                }, true));
            } catch (Exception ignored) {
            }
        });
    }

    /** El que revive mira la luz de la fogata (y luego la estrella que sube) mientras dura el ritual. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ACTIVE.isEmpty()) return;
        for (Active a : ACTIVE) {
            if (!a.reviverId.equals(mc.player.getUUID())) continue;
            float t = a.age + (float) e.getPartialTick();
            float f = ss(150, 180, t) * (1 - ss(571, 600, t));
            if (f <= 0.001f) continue;
            Vec3 cam = e.getCamera().getPosition();
            double ty = Mth.lerp(ss(400, 430, t), a.c.y + 4.0, starY(a.c.y, t));
            double dx = a.c.x - cam.x, dy = ty - cam.y, dz = a.c.z - cam.z, h = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
            e.setYaw(Mth.rotLerp(f, e.getYaw(), yaw));
            e.setPitch(Mth.lerp(f, e.getPitch(), pitch));
            return;
        }
    }

    public static Active nearest(Vec3 pos) {
        Active best = null;
        double bd = Double.MAX_VALUE;
        for (Active a : ACTIVE) {
            double d = a.c.distanceToSqr(pos);
            if (d < bd) { bd = d; best = a; }
        }
        return best;
    }

    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            restoreClouds(mc);
            return;
        }
        if (mc.isPaused()) return;

        boolean inSpace = false;
        Iterator<Active> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Active a = it.next();
            a.age++;
            spawn(mc.level, a);
            if (mc.player != null && a.c.distanceTo(mc.player.position()) < 90 && space(a.age) > 0.5f) inSpace = true;
            if (a.age >= TOTAL) it.remove();
        }
        // sin nubes mientras dura el vacio estrellado
        if (inSpace) {
            if (savedClouds == null) {
                savedClouds = mc.options.getCloudsType();
                mc.options.cloudStatus().set(CloudStatus.OFF);
            }
        } else {
            restoreClouds(mc);
        }
    }

    private static void restoreClouds(Minecraft mc) {
        if (savedClouds != null) {
            mc.options.cloudStatus().set(savedClouds);
            savedClouds = null;
        }
    }

    // ------------------------------------------------------------------ particulas
    private static void dust(Level l, Vector3f col, float size, double x, double y, double z) {
        l.addParticle(new DustParticleOptions(col, size), x, y, z, 0, 0, 0);
    }

    private static void spawn(Level l, Active a) {
        final int t = a.age;
        final double cx = a.c.x, cy = a.c.y, cz = a.c.z;

        // chispas alrededor del punto brillante del anillo
        if (t >= 20 && t < 140 && t % 2 == 0) {
            l.addParticle(ParticleTypes.END_ROD, cx + (R.nextDouble() - 0.5) * 4, cy + RING_HEIGHT + (R.nextDouble() - 0.5) * 3,
                    cz + (R.nextDouble() - 0.5) * 4, 0, -0.02, 0);
        }
        // columna fina de destellos que sube de la fogata al cielo
        if (t >= 215 && t < SPACE_END && t % 2 == 0) {
            l.addParticle(ParticleTypes.END_ROD, cx, cy + 1.8, cz, 0, 0.18, 0);
        }
        // chispas alrededor de la estrella dorada
        if (t >= 415 && t < SPACE_END) {
            int n = 1 + (t - 415) / 40;
            for (int i = 0; i < n; i++) {
                double yaw = R.nextDouble() * Math.PI * 2, pitch = (R.nextDouble() - 0.5) * Math.PI;
                double rad = 0.5 + R.nextDouble() * 3.0;
                double px = cx + Math.cos(yaw) * Math.cos(pitch) * rad;
                double py = starY(cy, t) + Math.sin(pitch) * rad;
                double pz = cz + Math.sin(yaw) * Math.cos(pitch) * rad;
                switch (R.nextInt(3)) {
                    case 0 -> l.addParticle(ParticleTypes.END_ROD, px, py, pz, 0, -0.01, 0);
                    case 1 -> l.addParticle(ParticleTypes.GLOW, px, py, pz, 0, -0.01, 0);
                    default -> dust(l, YELLOW, 0.6f, px, py, pz);
                }
            }
        }
        // estallido en el destello
        if (t == 571) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                float vol = (float) Mth.clamp((90 - a.c.distanceTo(mc.player.position())) / 60.0, 0.0, 1.0);
                if (vol > 0.02f) mc.getSoundManager().play(SimpleSoundInstance.forUI(RevivalCampfireMod.REVIVED.get(), 1.0f, vol));
            }
            for (int i = 0; i < 160; i++) {
                double yaw = R.nextDouble() * Math.PI * 2, pitch = (R.nextDouble() - 0.5) * Math.PI;
                double sp = 0.2 + R.nextDouble() * 0.7;
                l.addParticle(ParticleTypes.END_ROD, cx, starY(cy, t), cz,
                        Math.cos(yaw) * Math.cos(pitch) * sp, Math.sin(pitch) * sp, Math.sin(yaw) * Math.cos(pitch) * sp);
            }
        }
        // la figura desciende dejando polvo de luz
        if (t >= 571 && t < RitualManager.STAND_LAND) {
            Vec3 p = RitualManager.figurePos(a.c, t);
            for (int i = 0; i < 3; i++) {
                l.addParticle(ParticleTypes.END_ROD, p.x + (R.nextDouble() - 0.5), p.y + 1 + R.nextDouble() * 1.2,
                        p.z + (R.nextDouble() - 0.5), 0, -0.02, 0);
            }
        }
        // disipacion final
        if (t >= 613 && t < 713) {
            int n = (int) (6 * (713 - t) / 100f);
            for (int i = 0; i < n; i++) {
                l.addParticle(ParticleTypes.END_ROD, cx + (R.nextDouble() - 0.5) * 6, cy + R.nextDouble() * 4,
                        cz + (R.nextDouble() - 0.5) * 6, 0, 0.04, 0);
            }
        }
    }

    // ------------------------------------------------------------------ overlay 2D
    public static void renderOverlay(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ACTIVE.isEmpty()) return;
        Active a = nearest(mc.player.position());
        if (a == null) return;
        float prox = (float) Mth.clamp((110 - a.c.distanceTo(mc.player.position())) / 50.0, 0, 1);
        if (prox <= 0.01f) return;
        float t = a.age + pt;
        float sp = space(t);

        float dk = dusk(t) * (1 - sp) * prox * 0.38f;                 // oscurece el mundo
        if (dk > 0.01f) g.fill(0, 0, w, h, ((int) (dk * 255) << 24) | 0x000014);
        float au = aurora(t) * (1 - sp) * prox * 0.13f;               // brillo verde de las auroras
        if (au > 0.005f) g.fill(0, 0, w, h, ((int) (au * 255) << 24) | 0x3CFF78);
        float fl = flash(t) * prox;                                    // destello blanco
        if (fl > 0.01f) g.fill(0, 0, w, h, ((int) (Mth.clamp(fl, 0, 1) * 255) << 24) | 0xFFFFFF);
    }
}
