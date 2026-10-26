package com.revivalcampfire;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Modelo propio de la fogata (assets/revivalcampfire/models/custom/fogata.json).
 * Formato: { "caja": [...], "quads": [[x,y,z,u,v] x4, nx,ny,nz, material] ... } en unidades de 1/16 de bloque.
 * material 0 = atlas fogata.png, 1 = corteza de abeto de Minecraft, 2 = extremo (anillos) de abeto.
 */
public class FogataModel {
    private static final ResourceLocation JSON = new ResourceLocation(RevivalCampfireMod.MODID, "models/custom/fogata.json");
    public static final ResourceLocation[] TEXTURES = {
            new ResourceLocation(RevivalCampfireMod.MODID, "textures/block/fogata.png"),
            new ResourceLocation("minecraft", "textures/block/spruce_log.png"),
            new ResourceLocation("minecraft", "textures/block/spruce_log_top.png")
    };

    @SuppressWarnings("unchecked")
    private static final List<float[]>[] QUADS = new List[]{new ArrayList<float[]>(), new ArrayList<float[]>(), new ArrayList<float[]>()};
    private static boolean loaded = false;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResource(JSON).orElseThrow();
            try (Reader r = new InputStreamReader(res.open())) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                JsonArray quads = root.getAsJsonArray("quads");
                for (int i = 0; i < quads.size(); i++) {
                    JsonArray a = quads.get(i).getAsJsonArray();
                    float[] q = new float[a.size()];
                    for (int k = 0; k < q.length; k++) q[k] = a.get(k).getAsFloat();
                    int mat = q.length > 23 ? (int) q[23] : 0;
                    QUADS[Math.max(0, Math.min(2, mat))].add(q);
                }
            }
        } catch (Exception e) {
            System.err.println("[revivalcampfire] No se pudo cargar fogata.json: " + e);
        }
    }

    /** Dibuja el modelo con el origen en el centro-base del bloque (el PoseStack ya debe estar ahi). */
    public static void render(PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        load();
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int mat = 0; mat < 3; mat++) {
            if (QUADS[mat].isEmpty()) continue;
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEXTURES[mat]));
            for (float[] q : QUADS[mat]) {
                for (int k = 0; k < 4; k++) {
                    int o = k * 5;
                    vc.vertex(m, q[o] / 16f, q[o + 1] / 16f, q[o + 2] / 16f)
                            .color(255, 255, 255, 255)
                            .uv(q[o + 3], q[o + 4])
                            .overlayCoords(overlay)
                            .uv2(light)
                            .normal(n, q[20], q[21], q[22])
                            .endVertex();
                }
            }
        }
    }
}
