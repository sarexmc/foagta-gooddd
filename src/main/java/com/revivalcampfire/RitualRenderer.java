package com.revivalcampfire;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.Random;

import static com.revivalcampfire.ClientRitual.ss;

/**
 * Dibujo del ritual con geometria propia (sin librerias externas, solo el renderer de vanilla).
 *  AFTER_SKY               -> cielo crepuscular, estrellas, anillos de onda, auroras
 *  AFTER_TRIPWIRE_BLOCKS   -> el vacio (tapa el mundo), la fogata, discos, arcos, estrella dorada
 */
public class RitualRenderer {
    private static final double TAU = Math.PI * 2;
    private static final int NS = 900;
    private static final float[] SX = new float[NS], SY = new float[NS], SZ = new float[NS], SSZ = new float[NS], SPH = new float[NS];
    private static final int[] SCOL = new int[NS];

    static {
        Random r = new Random(1337);
        for (int i = 0; i < NS; i++) {
            double u = r.nextDouble() * 2 - 1, a = r.nextDouble() * TAU, s = Math.sqrt(1 - u * u);
            SX[i] = (float) (s * Math.cos(a));
            SY[i] = (float) u;
            SZ[i] = (float) (s * Math.sin(a));
            SSZ[i] = 0.12f + r.nextFloat() * r.nextFloat() * 0.55f;
            SPH[i] = r.nextFloat() * 6.28f;
            SCOL[i] = r.nextInt(10);
        }
    }

    // ------------------------------------------------------------------ entrada
    public static void onRenderLevel(RenderLevelStageEvent e) {
        RenderLevelStageEvent.Stage st = e.getStage();
        boolean sky = st == RenderLevelStageEvent.Stage.AFTER_SKY;
        boolean world = st == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS;
        if (!sky && !world) return;

        Camera camera = e.getCamera();
        Vec3 cam = camera.getPosition();
        ClientRitual.Active a = ClientRitual.nearest(cam);
        if (a == null) return;
        float prox = (float) Mth.clamp((110 - a.c.distanceTo(cam)) / 50.0, 0, 1);
        if (prox <= 0.01f) return;

        float t = a.age + e.getPartialTick();
        Matrix4f m = e.getPoseStack().last().pose();
        if (sky) renderSky(m, camera, cam, a, t, prox);
        else renderWorld(e.getPoseStack(), m, camera, cam, a, t, prox, e.getPartialTick());
    }

    // ------------------------------------------------------------------ estado GL
    private static void setup(boolean additive, boolean depthTest) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static void restore() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static BufferBuilder begin() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        return b;
    }

    private static void flush(BufferBuilder b) {
        BufferUploader.drawWithShader(b.end());
    }

    private static void vtx(BufferBuilder b, Matrix4f m, double x, double y, double z, float r, float g, float bl, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).color(r, g, bl, a).endVertex();
    }

    // ------------------------------------------------------------------ sprites de luz
    private static ResourceLocation fx(String n) { return new ResourceLocation(RevivalCampfireMod.MODID, "textures/effect/" + n + ".png"); }
    private static final ResourceLocation HALO = fx("halo"), RAYOS = fx("rayos"), LOBULOS = fx("lobulos"), HAZ = fx("haz"),
            AROS = fx("aros"), BRILLO = fx("brillo"), CENTRO = fx("centro"), ESTRELLA = fx("estrella"),
            CINTA = fx("cinta"), GRIETA = fx("grieta"), FUEGO = fx("fuego");

    private static void setupTex(boolean depthTest) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Estrella luminosa: brillo + halo + dos abanicos de rayos que giran + lobulos + estrella de cuatro puntas + nucleo. */
    private static void starSprites(Matrix4f m, Camera camera, double x, double y, double z, float size,
                                    float r, float g, float bl, float a, float t, boolean depthTest) {
        setupTex(depthTest);
        Vector3f L = camera.getLeftVector(), U = camera.getUpVector();
        sprite(BRILLO, m, x, y, z, L, U, size * 2.2, 0, r, g, bl, a * 0.8f);
        sprite(HALO, m, x, y, z, L, U, size * 2.8, 0, r, g, bl, a);
        sprite(RAYOS, m, x, y, z, L, U, size * 4.0, t * 0.004, r, g, bl, a * 0.9f);
        sprite(RAYOS, m, x, y, z, L, U, size * 3.0, 1.0 - t * 0.006, r, g, bl, a * 0.6f);
        sprite(LOBULOS, m, x, y, z, L, U, size * 3.2, t * 0.002, 1f, 1f, 1f, a * 0.8f);
        sprite(ESTRELLA, m, x, y, z, L, U, size * 3.4, 0, 1f, 1f, 1f, a);
        sprite(CENTRO, m, x, y, z, L, U, size * 1.0, 0, 1f, 1f, 1f, a);
        restore();
    }

    private static void sprite(ResourceLocation tex, Matrix4f m, double x, double y, double z, Vector3f L, Vector3f U,
                               double s, double rot, float r, float g, float bl, float a) {
        spriteUV(tex, m, x, y, z, L, U, s, s, rot, 0f, 1f, r, g, bl, a);
    }

    /** Billboard con region horizontal de la textura [u0,u1] y semiejes sx, sy. */
    private static void spriteUV(ResourceLocation tex, Matrix4f m, double x, double y, double z, Vector3f L, Vector3f U,
                                 double sx, double sy, double rot, float u0, float u1, float r, float g, float bl, float a) {
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        double c = Math.cos(rot), sn = Math.sin(rot);
        double ax = (c * L.x + sn * U.x) * sx, ay = (c * L.y + sn * U.y) * sx, az = (c * L.z + sn * U.z) * sx;
        double bx = (-sn * L.x + c * U.x) * sy, by = (-sn * L.y + c * U.y) * sy, bz = (-sn * L.z + c * U.z) * sy;
        b.vertex(m, (float) (x - ax - bx), (float) (y - ay - by), (float) (z - az - bz)).uv(u0, 1).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x + ax - bx), (float) (y + ay - by), (float) (z + az - bz)).uv(u1, 1).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x + ax + bx), (float) (y + ay + by), (float) (z + az + bz)).uv(u1, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x - ax + bx), (float) (y - ay + by), (float) (z - az + bz)).uv(u0, 0).color(r, g, bl, a).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    /** Tres circulos deformes (cian, lima, blanco) que respiran y giran en el cielo. */
    private static void ringSprites(Matrix4f m, Camera camera, double x, double y, double z, float t, float f) {
        setupTex(false);
        Vector3f L = camera.getLeftVector(), U = camera.getUpVector();
        float[][] col = {{1f, 1f, 1f}, {1f, 1f, 1f}, {1f, 1f, 1f}};
        for (int k = 0; k < 3; k++) {
            double base = 6.5 - k * 1.1;
            double sx = base * (1 + 0.09 * Math.sin(t * 0.13 + k * 2.1));       // se deforma al respirar
            double sy = base * (1 + 0.09 * Math.cos(t * 0.11 + k * 1.3));
            double rot = t * (k == 0 ? 0.025 : k == 1 ? -0.04 : 0.015) + k;
            float al = f * (0.85f + 0.15f * (float) Math.sin(t * 0.3 + k));
            spriteUV(AROS, m, x, y, z, L, U, sx, sy, rot, k / 3f, (k + 1) / 3f, col[k][0], col[k][1], col[k][2], al);
        }
        restore();
    }

    /** Grieta luminosa sobre el suelo (plano horizontal). */
    private static void groundSprite(ResourceLocation tex, Matrix4f m, double x, double y, double z, double s, double rot,
                                     float r, float g, float bl, float a) {
        double c = Math.cos(rot) * s, sn = Math.sin(rot) * s;
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, (float) (x - c + sn), (float) y, (float) (z - sn - c)).uv(0, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x + c + sn), (float) y, (float) (z + sn - c)).uv(1, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x + c - sn), (float) y, (float) (z + sn + c)).uv(1, 1).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x - c - sn), (float) y, (float) (z - sn + c)).uv(0, 1).color(r, g, bl, a).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    private static void cracks(Matrix4f m, double cx, double gy, double cz, float t, float f) {
        setupTex(true);
        float grow = ss(150, 185, t);
        groundSprite(GRIETA, m, cx, gy, cz, 5 + 26 * grow, 0.3, 0.3f, 1f, 0.9f, f);
        groundSprite(GRIETA, m, cx, gy + 0.01, cz, 3 + 17 * grow, 2.2 + t * 0.002, 0.7f, 1f, 0.3f, f * 0.9f);
        groundSprite(GRIETA, m, cx, gy + 0.02, cz, 2 + 11 * grow, 4.0 - t * 0.003, 0.3f, 1f, 0.9f, f * 0.8f);
        restore();
    }

    /** Arcos de luz (cinta.png) que orbitan la fogata en planos inclinados. */
    private static void arcsTex(Matrix4f m, double cx, double cy, double cz, float gy, float t, float f) {
        setupTex(true);
        RenderSystem.setShaderTexture(0, CINTA);
        final int N = 40;
        final double ARC = 3.6;
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 7; i++) {
            double R = (2.4 + 0.38 * i) * 1.8 * (0.8 + 0.5 * ss(215, 500, t));
            double tilt = Math.toRadians(52 + i * 5 + 8 * Math.sin(t * 0.01 + i));
            double yaw = i * 0.9 + t * 0.004 * (i % 2 == 0 ? 1 : -1);
            double nx = Math.sin(tilt) * Math.cos(yaw), ny = Math.cos(tilt), nz = Math.sin(tilt) * Math.sin(yaw);
            double ul = Math.sqrt(nz * nz + nx * nx);
            double ux = -nz / ul, uy = 0, uz = nx / ul;
            double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
            double spin = (i % 2 == 0 ? 1 : -1) * (0.022 + 0.003 * i) * t + i;
            double ccy = cy + 2.6;
            boolean lime = i % 2 == 0;
            float r = lime ? 0.72f : 0.15f, g = lime ? 1f : 0.85f, bl = lime ? 0.12f : 1f;
            double w = 0.16 + 0.03 * i;
            for (int k = 0; k < N; k++) {
                double p0 = spin + ARC * k / N, p1 = spin + ARC * (k + 1) / N;
                double c0 = Math.cos(p0), s0 = Math.sin(p0), c1 = Math.cos(p1), s1 = Math.sin(p1);
                double x0 = cx + R * (c0 * ux + s0 * vx), y0 = ccy + R * (c0 * uy + s0 * vy), z0 = cz + R * (c0 * uz + s0 * vz);
                double x1 = cx + R * (c1 * ux + s1 * vx), y1 = ccy + R * (c1 * uy + s1 * vy), z1 = cz + R * (c1 * uz + s1 * vz);
                if (y0 < gy || y1 < gy) continue;
                float a0 = f * 0.95f * (float) Math.pow(Math.sin(Math.PI * k / N), 0.8);
                float a1 = f * 0.95f * (float) Math.pow(Math.sin(Math.PI * (k + 1) / N), 0.8);
                float uA = (float) (k * 0.25 + t * 0.01), uB = (float) ((k + 1) * 0.25 + t * 0.01);
                texRibbon(b, m, x0, y0, z0, x1, y1, z1, nx * w, ny * w, nz * w, uA, uB, r, g, bl, a0, a1);
                texRibbon(b, m, x0, y0, z0, x1, y1, z1, (c0 * ux + s0 * vx) * w, (c0 * uy + s0 * vy) * w, (c0 * uz + s0 * vz) * w, uA, uB, r, g, bl, a0, a1);
            }
        }
        BufferUploader.drawWithShader(b.end());
        restore();
    }

    private static void texRibbon(BufferBuilder b, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1,
                                  double ox, double oy, double oz, float uA, float uB, float r, float g, float bl, float a0, float a1) {
        b.vertex(m, (float) (x0 - ox), (float) (y0 - oy), (float) (z0 - oz)).uv(uA, 0).color(r, g, bl, a0).endVertex();
        b.vertex(m, (float) (x0 + ox), (float) (y0 + oy), (float) (z0 + oz)).uv(uA, 1).color(r, g, bl, a0).endVertex();
        b.vertex(m, (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz)).uv(uB, 1).color(r, g, bl, a1).endVertex();
        b.vertex(m, (float) (x1 - ox), (float) (y1 - oy), (float) (z1 - oz)).uv(uB, 0).color(r, g, bl, a1).endVertex();
    }

    /** Haz vertical de luz (siempre de frente a la camara) desde el suelo hasta el cielo. */
    private static void beam(Matrix4f m, double x, double y, double z, double w, double h, float r, float g, float bl, float a) {
        setupTex(true);
        double len = Math.sqrt(x * x + z * z);
        if (len < 1e-4) { restore(); return; }
        double rx = z / len * w, rz = -x / len * w;      // perpendicular horizontal a la linea de vision
        RenderSystem.setShaderTexture(0, HAZ);
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.vertex(m, (float) (x - rx), (float) (y + h), (float) (z - rz)).uv(0, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x + rx), (float) (y + h), (float) (z + rz)).uv(1, 0).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x + rx), (float) y, (float) (z + rz)).uv(1, 1).color(r, g, bl, a).endVertex();
        b.vertex(m, (float) (x - rx), (float) y, (float) (z - rz)).uv(0, 1).color(r, g, bl, a).endVertex();
        BufferUploader.drawWithShader(b.end());
        restore();
    }

    // ------------------------------------------------------------------ cielo
    private static void renderSky(Matrix4f m, Camera camera, Vec3 cam, ClientRitual.Active a, float t, float prox) {
        float du = ClientRitual.dusk(t) * prox;
        if (du > 0.01f) {
            setup(false, false);
            BufferBuilder b = begin();
            dome(b, m, du);
            flush(b);
        }
        setup(true, false);
        float sf = ClientRitual.starFade(t) * prox;
        if (sf > 0.01f) { BufferBuilder b = begin(); stars(b, m, t, sf, false); flush(b); }
        float au = ClientRitual.aurora(t) * prox;
        if (au > 0.01f) { BufferBuilder b = begin(); aurora(b, m, t, au); flush(b); }
        float rf = ClientRitual.ringFade(t) * prox;
        if (rf > 0.01f) {
            double rx = a.c.x - cam.x, ry = a.c.y + ClientRitual.RING_HEIGHT - cam.y, rz = a.c.z - cam.z;
            ringSprites(m, camera, rx, ry, rz, t, rf);
            float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.2);
            starSprites(m, camera, rx, ry, rz, 0.9f * pulse, 0.7f, 1f, 1f, rf, t, false);
        }
        restore();
    }

    /** Dos anillos inclinados (cian y lima) que giran despacio con un punto brillante en el centro. */
    private static void skyRing(BufferBuilder b, Matrix4f m, Camera camera, double cx, double cy, double cz, float t, float f) {
        final double tilt = Math.toRadians(28);
        final int N = 72;
        for (int k = 0; k < 2; k++) {
            double R = k == 0 ? 3.4 : 2.7;
            float r = k == 0 ? 0.25f : 0.72f, g = k == 0 ? 0.92f : 1f, bl = k == 0 ? 1f : 0.15f;
            double spin = t * (k == 0 ? 0.03 : -0.045);
            for (int i = 0; i < N; i++) {
                double p0 = spin + TAU * i / N, p1 = spin + TAU * (i + 1) / N;
                double x0 = R * Math.cos(p0), z0 = R * Math.sin(p0), x1 = R * Math.cos(p1), z1 = R * Math.sin(p1);
                double y0 = -z0 * Math.sin(tilt), y1 = -z1 * Math.sin(tilt);
                double zz0 = z0 * Math.cos(tilt), zz1 = z1 * Math.cos(tilt);
                float al = f * (0.5f + 0.5f * (float) Math.sin(i * 0.9 + k * 2));   // trazo irregular
                ribbon(b, m, cx + x0, cy + y0, cz + zz0, cx + x1, cy + y1, cz + zz1, 0, 0.10, 0, r, g, bl, al, al);
                ribbon(b, m, cx + x0, cy + y0, cz + zz0, cx + x1, cy + y1, cz + zz1, Math.cos(p0) * 0.10, 0, Math.sin(p0) * 0.10, r, g, bl, al, al);
            }
        }
    }

    private static void dome(BufferBuilder b, Matrix4f m, float a) {
        final int LAT = 14, AZ = 32;
        final double R = 95;
        for (int i = 0; i < LAT; i++) {
            double e0 = Math.toRadians(-12 + 102.0 * i / LAT), e1 = Math.toRadians(-12 + 102.0 * (i + 1) / LAT);
            float[] c0 = domeColor(e0), c1 = domeColor(e1);
            for (int j = 0; j < AZ; j++) {
                double a0 = TAU * j / AZ, a1 = TAU * (j + 1) / AZ;
                vtx(b, m, R * Math.cos(e0) * Math.cos(a0), R * Math.sin(e0), R * Math.cos(e0) * Math.sin(a0), c0[0], c0[1], c0[2], a);
                vtx(b, m, R * Math.cos(e1) * Math.cos(a0), R * Math.sin(e1), R * Math.cos(e1) * Math.sin(a0), c1[0], c1[1], c1[2], a);
                vtx(b, m, R * Math.cos(e1) * Math.cos(a1), R * Math.sin(e1), R * Math.cos(e1) * Math.sin(a1), c1[0], c1[1], c1[2], a);
                vtx(b, m, R * Math.cos(e0) * Math.cos(a1), R * Math.sin(e0), R * Math.cos(e0) * Math.sin(a1), c0[0], c0[1], c0[2], a);
            }
        }
    }

    private static float[] domeColor(double elev) {
        float k = (float) Math.pow(Mth.clamp(elev / (Math.PI / 2), 0, 1), 0.6);
        return new float[]{Mth.lerp(k, 0.09f, 0.015f), Mth.lerp(k, 0.13f, 0.025f), Mth.lerp(k, 0.30f, 0.10f)};
    }

    private static void stars(BufferBuilder b, Matrix4f m, float t, float a, boolean space) {
        final double R = 90;
        final float boost = space ? 1.35f : 1.0f;
        for (int i = 0; i < NS; i++) {
            boolean big = i % 25 == 0;
            float tw = 0.6f + 0.4f * (float) Math.sin(t * 0.09 + SPH[i]);
            float al = Mth.clamp(a * tw * (big ? 1f : 0.55f + 0.45f * SSZ[i] * 1.8f), 0, 1);
            float r = 1f, g = 1f, bl = 1f;
            if (SCOL[i] >= 8) { r = 0.6f; g = 0.9f; }
            else if (SCOL[i] >= 6) { bl = 0.6f; g = 0.95f; }
            double dx = SX[i], dy = SY[i], dz = SZ[i];
            double ux, uy, uz;
            if (Math.abs(dy) > 0.98) { ux = 1; uy = 0; uz = 0; }
            else { double l = Math.sqrt(dz * dz + dx * dx); ux = -dz / l; uy = 0; uz = dx / l; }
            double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
            double s = SSZ[i] * boost * (big ? 2.4 : 1.0);
            double px = dx * R, py = dy * R, pz = dz * R;
            quadUV(b, m, px, py, pz, ux * s, uy * s, uz * s, vx * s, vy * s, vz * s, r, g, bl, al);
            if (big) {
                quadUV(b, m, px, py, pz, ux * s * 5, uy * s * 5, uz * s * 5, vx * s * 0.18, vy * s * 0.18, vz * s * 0.18, r, g, bl, al * 0.8f);
                quadUV(b, m, px, py, pz, ux * s * 0.18, uy * s * 0.18, uz * s * 0.18, vx * s * 5, vy * s * 5, vz * s * 5, r, g, bl, al * 0.8f);
            }
        }
    }

    /** Cuadrilatero centrado en P con semiejes U y V. */
    private static void quadUV(BufferBuilder b, Matrix4f m, double px, double py, double pz,
                               double ux, double uy, double uz, double vx, double vy, double vz,
                               float r, float g, float bl, float a) {
        vtx(b, m, px - ux - vx, py - uy - vy, pz - uz - vz, r, g, bl, a);
        vtx(b, m, px + ux - vx, py + uy - vy, pz + uz - vz, r, g, bl, a);
        vtx(b, m, px + ux + vx, py + uy + vy, pz + uz + vz, r, g, bl, a);
        vtx(b, m, px - ux + vx, py - uy + vy, pz - uz + vz, r, g, bl, a);
    }

    /** Anillos de onda concentricos que bajan desde el cenit. */
    private static void skyRipples(BufferBuilder b, Matrix4f m, float t, float f) {
        final double R = 88;
        final int AZ = 64;
        for (int i = 0; i < 5; i++) {
            float deg = 6 + ((t * 0.28f + i * 15) % 75);
            double phi = Math.toRadians(deg);
            double w = Math.toRadians(2.5 + deg * 0.06);
            float al = 0.30f * (1 - deg / 85f) * f;
            for (int j = 0; j < AZ; j++) {
                double a0 = TAU * j / AZ, a1 = TAU * (j + 1) / AZ;
                polarBand(b, m, R, phi - w, phi, a0, a1, 0f, al);
                polarBand(b, m, R, phi, phi + w, a0, a1, al, 0f);
            }
        }
        // diamante brillante en el cenit
        quadUV(b, m, 0, 88, 0, 6, 0, 0, 0, 0, 0.4, 0.7f, 0.95f, 1f, 0.9f * f);
        quadUV(b, m, 0, 88, 0, 0.4, 0, 0, 0, 0, 6, 0.7f, 0.95f, 1f, 0.9f * f);
    }

    private static void polarBand(BufferBuilder b, Matrix4f m, double R, double p0, double p1, double a0, double a1, float al0, float al1) {
        float cr = 0.55f, cg = 0.82f, cb = 1f;
        vtx(b, m, R * Math.sin(p0) * Math.cos(a0), R * Math.cos(p0), R * Math.sin(p0) * Math.sin(a0), cr, cg, cb, al0);
        vtx(b, m, R * Math.sin(p1) * Math.cos(a0), R * Math.cos(p1), R * Math.sin(p1) * Math.sin(a0), cr, cg, cb, al1);
        vtx(b, m, R * Math.sin(p1) * Math.cos(a1), R * Math.cos(p1), R * Math.sin(p1) * Math.sin(a1), cr, cg, cb, al1);
        vtx(b, m, R * Math.sin(p0) * Math.cos(a1), R * Math.cos(p0), R * Math.sin(p0) * Math.sin(a1), cr, cg, cb, al0);
    }

    /** Cortinas verticales de luz azul/verde. */
    private static void aurora(BufferBuilder b, Matrix4f m, float t, float f) {
        final int N = 72;
        final double R = 88;
        final double[] el = {Math.toRadians(4), Math.toRadians(25), Math.toRadians(50), Math.toRadians(78)};
        final float[] al = {0.0f, 0.22f, 0.30f, 0.42f};
        for (int k = 0; k < N; k++) {
            double a0 = TAU * k / N, a1 = TAU * (k + 1) / N;
            float streak = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(k * 1.7 + t * 0.045 + Math.sin(k * 0.37) * 3));
            float hue = 0.5f + 0.5f * (float) Math.sin(k * 0.5 + t * 0.01);
            for (int s = 0; s < 3; s++) {
                float lo = s / 3f, hi = (s + 1) / 3f;
                float r0 = Mth.lerp(lo, 0.2f, 0.35f) * (1 - 0.3f * hue), g0 = Mth.lerp(lo, 1f, 0.85f), b0 = Mth.lerp(lo, 0.45f, 1f);
                float r1 = Mth.lerp(hi, 0.2f, 0.35f) * (1 - 0.3f * hue), g1 = Mth.lerp(hi, 1f, 0.85f), b1 = Mth.lerp(hi, 0.45f, 1f);
                float a0v = al[s] * streak * f, a1v = al[s + 1] * streak * f;
                vtx(b, m, R * Math.cos(el[s]) * Math.cos(a0), R * Math.sin(el[s]), R * Math.cos(el[s]) * Math.sin(a0), r0, g0, b0, a0v);
                vtx(b, m, R * Math.cos(el[s + 1]) * Math.cos(a0), R * Math.sin(el[s + 1]), R * Math.cos(el[s + 1]) * Math.sin(a0), r1, g1, b1, a1v);
                vtx(b, m, R * Math.cos(el[s + 1]) * Math.cos(a1), R * Math.sin(el[s + 1]), R * Math.cos(el[s + 1]) * Math.sin(a1), r1, g1, b1, a1v);
                vtx(b, m, R * Math.cos(el[s]) * Math.cos(a1), R * Math.sin(el[s]), R * Math.cos(el[s]) * Math.sin(a1), r0, g0, b0, a0v);
            }
        }
    }

    // ------------------------------------------------------------------ mundo
    private static void renderWorld(PoseStack ps, Matrix4f m, Camera camera, Vec3 cam, ClientRitual.Active a, float t, float prox, float pt) {
        float sp = ClientRitual.space(t) * prox;
        double rx = a.c.x - cam.x, ry = a.c.y - cam.y, rz = a.c.z - cam.z;
        double gy = ry - 0.45;                       // un pelin sobre el suelo

        // cubo oscuro que cae desde el anillo y crece hasta tragarse el mundo
        float cs = ClientRitual.cubeSize(t);
        if (cs > 0.01f) drawCube(m, rx, ClientRitual.cubeY(a.c.y, t) - cam.y, rz, cs / 2.0, prox);

        if (sp > 0.001f) {
            // 1) el vacio tapa el mundo entero
            setup(false, false);
            BufferBuilder b = begin();
            cover(b, m, sp);
            flush(b);
            restore();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            // 2) la fogata se redibuja encima (con su propia profundidad)
            drawCampfire(ps, a, cam);
            // 3) estrellas, discos de luz
            setup(true, true);
            b = begin();
            stars(b, m, t, sp, true);
            spaceGround(b, m, rx, gy, rz, t, ss(180, 210, t) * sp);
            flush(b);
            restore();
        }

        // arcos de luz que orbitan la fogata (tambien de dia)
        float af = ss(215, 245, t) * (1 - ss(663, 718, t)) * prox;
        if (af > 0.01f) arcsTex(m, rx, ry, rz, (float) gy, t, af);
        // grietas luminosas en el suelo desde el impacto del cubo
        float cr = ss(150, 162, t) * (1 - ss(570, 590, t)) * prox;
        if (cr > 0.01f) cracks(m, rx, gy, rz, t, cr);
        // anillo claro en el suelo tras el destello
        float df = ss(570, 584, t) * (1 - ss(634, 694, t)) * prox;
        if (df > 0.01f) {
            setup(true, true);
            BufferBuilder b = begin();
            ring(b, m, rx, gy, rz, 8.8, 10.8, 96, 0.8f, 1f, 0.7f, 0f, 0.8f, 1f, 0.7f, 0.6f * df);
            ring(b, m, rx, gy, rz, 10.8, 13.6, 96, 0.8f, 1f, 0.7f, 0.6f * df, 0.8f, 1f, 0.7f, 0f);
            flush(b);
            restore();
        }

        // estrella dorada: crece y SUBE hasta que la pantalla se vuelve blanca
        float gs = ss(375, 425, t) * (1 - ss(571, 577, t)) * prox;
        if (gs > 0.01f) {
            float size = 0.45f + 4.5f * ss(395, 566, t) + 12f * ss(520, 571, t);
            starSprites(m, camera, rx, ClientRitual.starY(a.c.y, t) - cam.y, rz, size, 1f, 0.92f, 0.45f, gs, t, true);
        }
        // avatar del jugador revivido + resplandor + haz de luz
        if (t >= RitualManager.STAND_SPAWN && t < RitualManager.TOTAL) {
            Vec3 p = RitualManager.figurePos(a.c, t);
            float k = Mth.clamp((t - RitualManager.STAND_SPAWN) / (float) (RitualManager.STAND_LAND - RitualManager.STAND_SPAWN), 0f, 1f);
            drawAvatar(ps, a, cam, p, k, t, pt);
            if (t < 673) {
                float fa = (1 - ss(623, 673, t)) * prox;
                if (fa > 0.01f) {
                    starSprites(m, camera, p.x - cam.x, p.y + 1.0 - cam.y, p.z - cam.z,
                            0.4f + 2.6f * (1 - ss(603, 668, t)), 1f, 1f, 0.85f, fa, t, true);
                }
                Vec3 land = RitualManager.figurePos(a.c, RitualManager.STAND_LAND);
                float ba = ss(571, 585, t) * (1 - ss(640, 673, t)) * prox;
                if (ba > 0.01f) beam(m, land.x - cam.x, land.y - cam.y, land.z - cam.z, 1.6 + 0.4 * Math.sin(t * 0.2), 60, 0.85f, 1f, 0.6f, ba * 0.8f);
            }
        }
    }

    /** Dibuja al jugador revivido con su modelo y skin (silueta oscura mientras baja del cielo). */
    private static void drawAvatar(PoseStack ps, ClientRitual.Active a, Vec3 cam, Vec3 p, float k, float t, float pt) {
        AvatarPlayer av = ClientRitual.avatar(a);
        if (av == null) return;
        float yaw = k < 1f ? t * 20f * (1f - k)
                : (float) Math.toDegrees(Math.atan2(-(a.c.x - p.x), a.c.z - p.z));
        av.setPos(p.x, p.y, p.z);
        av.xo = av.xOld = p.x;
        av.yo = av.yOld = p.y;
        av.zo = av.zOld = p.z;
        av.setYRot(yaw);
        av.yRotO = yaw;
        av.yBodyRot = av.yBodyRotO = yaw;
        av.yHeadRot = av.yHeadRotO = yaw;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        if (k < 1f) RenderSystem.setShaderColor(0.12f, 0.16f, 0.2f, 1f);
        mc.getEntityRenderDispatcher().render(av, p.x - cam.x, p.y - cam.y, p.z - cam.z, yaw, pt, ps, buf, LightTexture.FULL_BRIGHT);
        buf.endBatch();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static final int[][] CORNERS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};

    private static void drawCube(Matrix4f m, double cx, double cy, double cz, double h, float prox) {
        setup(false, true);
        BufferBuilder b = begin();
        float a = 0.97f * prox;
        for (int ax = 0; ax < 3; ax++) {
            for (int sg = -1; sg <= 1; sg += 2) {
                int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
                for (int[] c : CORNERS) {
                    double[] p = new double[3];
                    p[ax] = sg * h; p[a1] = c[0] * h; p[a2] = c[1] * h;
                    vtx(b, m, cx + p[0], cy + p[1], cz + p[2], 0.01f, 0.02f, 0.05f, a);
                }
            }
        }
        flush(b);
        setup(true, true);
        b = begin();
        double w = Math.max(0.03, h * 0.025);
        float ea = 0.9f * prox;
        for (int ax = 0; ax < 3; ax++) {
            int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
            for (int s1 = -1; s1 <= 1; s1 += 2) {
                for (int s2 = -1; s2 <= 1; s2 += 2) {
                    double[] p0 = new double[3], p1 = new double[3], o1 = new double[3], o2 = new double[3];
                    p0[ax] = -h; p1[ax] = h;
                    p0[a1] = p1[a1] = s1 * h; p0[a2] = p1[a2] = s2 * h;
                    o1[a1] = w; o2[a2] = w;
                    ribbon(b, m, cx + p0[0], cy + p0[1], cz + p0[2], cx + p1[0], cy + p1[1], cz + p1[2], o1[0], o1[1], o1[2], 0.3f, 0.95f, 1f, ea, ea);
                    ribbon(b, m, cx + p0[0], cy + p0[1], cz + p0[2], cx + p1[0], cy + p1[1], cz + p1[2], o2[0], o2[1], o2[2], 0.3f, 0.95f, 1f, ea, ea);
                }
            }
        }
        flush(b);
        restore();
    }

    private static void drawCampfire(PoseStack ps, ClientRitual.Active a, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(a.c.x - cam.x, a.c.y - 0.5 - cam.y, a.c.z - cam.z);
        FogataModel.render(ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        buf.endBatch();
    }

    /** Cubo negro alrededor de la camara: oculta terreno, arboles y entidades. */
    private static void cover(BufferBuilder b, Matrix4f m, float a) {
        final float h = 40f;
        final int[][] cs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int ax = 0; ax < 3; ax++) {
            for (int sg = -1; sg <= 1; sg += 2) {
                int a1 = (ax + 1) % 3, a2 = (ax + 2) % 3;
                for (int[] c : cs) {
                    float[] p = new float[3];
                    p[ax] = sg * h; p[a1] = c[0] * h; p[a2] = c[1] * h;
                    vtx(b, m, p[0], p[1], p[2], 0f, 0f, 0f, a);
                }
            }
        }
    }

    /** Discos teñidos de turquesa/lima en el suelo del vacio, ondas que se expanden y la cortina del horizonte. */
    private static void spaceGround(BufferBuilder b, Matrix4f m, double cx, double gy, double cz, float t, float df) {
        if (df <= 0.001f) return;
        // halo interior alrededor de la fogata
        ring(b, m, cx, gy, cz, 4.4, 7.5, 64, 0.1f, 0.9f, 0.85f, 0f, 0.2f, 0.95f, 0.9f, 0.45f * df);
        ring(b, m, cx, gy, cz, 7.5, 8.1, 64, 1f, 1f, 1f, 0.9f * df, 1f, 1f, 1f, 0.9f * df);
        ring(b, m, cx, gy, cz, 8.1, 11.4, 64, 0.3f, 0.95f, 0.95f, 0.55f * df, 0.3f, 0.95f, 0.95f, 0f);
        // disco grande: hueco negro en el centro, brillo turquesa -> lima hacia afuera
        double p = 2 * Math.sin(t * 0.04);
        ring(b, m, cx, gy, cz, 20 + p, 26 + p, 128, 0f, 0.75f, 0.65f, 0f, 0f, 0.75f, 0.65f, 0.55f * df);
        ring(b, m, cx, gy, cz, 26 + p, 50, 128, 0f, 0.75f, 0.65f, 0.55f * df, 0.5f, 0.9f, 0.15f, 0.38f * df);
        ring(b, m, cx, gy, cz, 50, 70, 128, 0.5f, 0.9f, 0.15f, 0.38f * df, 0.3f, 1f, 0.5f, 0f);
        ring(b, m, cx, gy, cz, 63, 65.5, 128, 0.7f, 1f, 1f, 0f, 0.9f, 1f, 1f, 0.85f * df);
        ring(b, m, cx, gy, cz, 65.5, 68, 128, 0.9f, 1f, 1f, 0.85f * df, 0.4f, 1f, 0.6f, 0f);
        // ondas que se expanden
        for (int i = 0; i < 3; i++) {
            float ph = (((t - 185) * 0.55f) + i * 28) % 90;
            if (ph < 0) continue;
            double r = 9 + ph * 0.6, w = 0.6 + ph * 0.04;
            float al = 0.55f * (1 - ph / 90f) * df;
            ring(b, m, cx, gy, cz, r - w, r, 96, 0.7f, 1f, 1f, 0f, 0.7f, 1f, 1f, al);
            ring(b, m, cx, gy, cz, r, r + w, 96, 0.7f, 1f, 1f, al, 0.7f, 1f, 1f, 0f);
        }
        // cortina de luz en el horizonte
        final int N = 128;
        for (int i = 0; i < N; i++) {
            double a0 = TAU * i / N, a1 = TAU * (i + 1) / N;
            float hue = 0.5f + 0.5f * (float) Math.sin(i * 0.4 + t * 0.02);
            float r = Mth.lerp(hue, 0.1f, 0.55f), g = Mth.lerp(hue, 0.9f, 1f), bl = Mth.lerp(hue, 0.8f, 0.25f);
            vtx(b, m, cx + 66 * Math.cos(a0), gy, cz + 66 * Math.sin(a0), r, g, bl, 0.55f * df);
            vtx(b, m, cx + 66 * Math.cos(a0), gy + 7, cz + 66 * Math.sin(a0), r, g, bl, 0f);
            vtx(b, m, cx + 66 * Math.cos(a1), gy + 7, cz + 66 * Math.sin(a1), r, g, bl, 0f);
            vtx(b, m, cx + 66 * Math.cos(a1), gy, cz + 66 * Math.sin(a1), r, g, bl, 0.55f * df);
        }
    }

    private static void ring(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, double r0, double r1, int segs,
                             float cr0, float cg0, float cb0, float a0, float cr1, float cg1, float cb1, float a1) {
        for (int i = 0; i < segs; i++) {
            double t0 = TAU * i / segs, t1 = TAU * (i + 1) / segs;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            vtx(b, m, cx + c0 * r0, cy, cz + s0 * r0, cr0, cg0, cb0, a0);
            vtx(b, m, cx + c0 * r1, cy, cz + s0 * r1, cr1, cg1, cb1, a1);
            vtx(b, m, cx + c1 * r1, cy, cz + s1 * r1, cr1, cg1, cb1, a1);
            vtx(b, m, cx + c1 * r0, cy, cz + s1 * r0, cr0, cg0, cb0, a0);
        }
    }

    /** Arcos de energia (lima y cian) que giran en planos inclinados alrededor de la fogata. */
    private static void arcs(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, float gy, float t, float f) {
        final int N = 36;
        final double ARC = 3.6;
        for (int i = 0; i < 7; i++) {
            double R = (2.4 + 0.38 * i) * (0.8 + 0.5 * ss(215, 500, t));
            double tilt = Math.toRadians(52 + i * 5 + 8 * Math.sin(t * 0.01 + i));
            double yaw = i * 0.9 + t * 0.004 * (i % 2 == 0 ? 1 : -1);
            double nx = Math.sin(tilt) * Math.cos(yaw), ny = Math.cos(tilt), nz = Math.sin(tilt) * Math.sin(yaw);
            double ul = Math.sqrt(nz * nz + nx * nx);
            double ux = -nz / ul, uy = 0, uz = nx / ul;
            double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
            double spin = (i % 2 == 0 ? 1 : -1) * (0.022 + 0.003 * i) * t + i;
            double ccy = cy + 1.5;
            float r = i % 2 == 0 ? 0.72f : 0.15f, g = i % 2 == 0 ? 1f : 0.85f, bl = i % 2 == 0 ? 0.12f : 1f;
            double w = 0.07 + 0.015 * i;
            for (int k = 0; k < N; k++) {
                double p0 = spin + ARC * k / N, p1 = spin + ARC * (k + 1) / N;
                double c0 = Math.cos(p0), s0 = Math.sin(p0), c1 = Math.cos(p1), s1 = Math.sin(p1);
                double x0 = cx + R * (c0 * ux + s0 * vx), y0 = ccy + R * (c0 * uy + s0 * vy), z0 = cz + R * (c0 * uz + s0 * vz);
                double x1 = cx + R * (c1 * ux + s1 * vx), y1 = ccy + R * (c1 * uy + s1 * vy), z1 = cz + R * (c1 * uz + s1 * vz);
                if (y0 < gy || y1 < gy) continue;
                float a0 = f * 0.95f * (float) Math.pow(Math.sin(Math.PI * k / N), 0.8);
                float a1 = f * 0.95f * (float) Math.pow(Math.sin(Math.PI * (k + 1) / N), 0.8);
                // cinta 1: ancho a lo largo de la normal del plano
                ribbon(b, m, x0, y0, z0, x1, y1, z1, nx * w, ny * w, nz * w, r, g, bl, a0, a1);
                // cinta 2: ancho radial (para verla de canto)
                ribbon(b, m, x0, y0, z0, x1, y1, z1,
                        (c0 * ux + s0 * vx) * w, (c0 * uy + s0 * vy) * w, (c0 * uz + s0 * vz) * w, r, g, bl, a0, a1);
            }
        }
    }

    private static void ribbon(BufferBuilder b, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1,
                               double ox, double oy, double oz, float r, float g, float bl, float a0, float a1) {
        vtx(b, m, x0 - ox, y0 - oy, z0 - oz, r, g, bl, a0);
        vtx(b, m, x0 + ox, y0 + oy, z0 + oz, r, g, bl, a0);
        vtx(b, m, x1 + ox, y1 + oy, z1 + oz, r, g, bl, a1);
        vtx(b, m, x1 - ox, y1 - oy, z1 - oz, r, g, bl, a1);
    }

    /** Estrella luminosa (billboard): nucleo difuso, destellos en cruz y rayos giratorios. */
    private static void glow(BufferBuilder b, Matrix4f m, Camera cam, double x, double y, double z,
                             float size, float r, float g, float bl, float a, float t) {
        Vector3f L = cam.getLeftVector(), U = cam.getUpVector();
        final int RINGS = 8, SEG = 20;
        for (int k = 0; k < RINGS; k++) {
            double r0 = size * k / RINGS, r1 = size * (k + 1) / RINGS;
            float a0 = a * (float) Math.pow(1 - k / (float) RINGS, 2.2), a1 = a * (float) Math.pow(1 - (k + 1) / (float) RINGS, 2.2);
            for (int j = 0; j < SEG; j++) {
                double t0 = TAU * j / SEG, t1 = TAU * (j + 1) / SEG;
                double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
                vtx(b, m, x + (c0 * L.x + s0 * U.x) * r0, y + (c0 * L.y + s0 * U.y) * r0, z + (c0 * L.z + s0 * U.z) * r0, r, g, bl, a0);
                vtx(b, m, x + (c0 * L.x + s0 * U.x) * r1, y + (c0 * L.y + s0 * U.y) * r1, z + (c0 * L.z + s0 * U.z) * r1, r, g, bl, a1);
                vtx(b, m, x + (c1 * L.x + s1 * U.x) * r1, y + (c1 * L.y + s1 * U.y) * r1, z + (c1 * L.z + s1 * U.z) * r1, r, g, bl, a1);
                vtx(b, m, x + (c1 * L.x + s1 * U.x) * r0, y + (c1 * L.y + s1 * U.y) * r0, z + (c1 * L.z + s1 * U.z) * r0, r, g, bl, a0);
            }
        }
        // destellos en cruz
        spike(b, m, x, y, z, L.x, L.y, L.z, U.x, U.y, U.z, size * 3.2, size * 0.06, r, g, bl, a * 0.9f);
        spike(b, m, x, y, z, -L.x, -L.y, -L.z, U.x, U.y, U.z, size * 3.2, size * 0.06, r, g, bl, a * 0.9f);
        spike(b, m, x, y, z, U.x, U.y, U.z, L.x, L.y, L.z, size * 3.2, size * 0.06, r, g, bl, a * 0.9f);
        spike(b, m, x, y, z, -U.x, -U.y, -U.z, L.x, L.y, L.z, size * 3.2, size * 0.06, r, g, bl, a * 0.9f);
        // rayos giratorios
        for (int i = 0; i < 10; i++) {
            double ang = i * 0.628 + t * 0.01;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double dx = ca * L.x + sa * U.x, dy = ca * L.y + sa * U.y, dz = ca * L.z + sa * U.z;
            double px = -sa * L.x + ca * U.x, py = -sa * L.y + ca * U.y, pz = -sa * L.z + ca * U.z;
            spike(b, m, x, y, z, dx, dy, dz, px, py, pz, size * (2.0 + 1.2 * Math.sin(t * 0.07 + i * 1.7)), size * 0.05, r, g, bl, a * 0.45f);
        }
    }

    /** Triangulo luminoso desde el centro (opaco) hasta la punta (transparente). */
    private static void spike(BufferBuilder b, Matrix4f m, double x, double y, double z,
                              double dx, double dy, double dz, double px, double py, double pz,
                              double len, double w, float r, float g, float bl, float a) {
        vtx(b, m, x - px * w, y - py * w, z - pz * w, r, g, bl, a);
        vtx(b, m, x + px * w, y + py * w, z + pz * w, r, g, bl, a);
        vtx(b, m, x + dx * len + px * w * 0.2, y + dy * len + py * w * 0.2, z + dz * len + pz * w * 0.2, r, g, bl, 0f);
        vtx(b, m, x + dx * len - px * w * 0.2, y + dy * len - py * w * 0.2, z + dz * len - pz * w * 0.2, r, g, bl, 0f);
    }
}
