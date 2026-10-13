package dev.novautils.utils;

import dev.novautils.NovaUtils;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A filled-box render type identical to vanilla's debug filled box, except the depth test always
 * passes - so what we draw shows through walls. Built once, lazily, via reflection because vanilla
 * keeps the snippet and the RenderType factory private.
 */
public final class EspRender {
    private static RenderType cached;
    private static boolean failed;

    private EspRender() {}

    public static RenderType filledNoDepth() {
        if (cached != null) return cached;
        if (failed) return RenderTypes.debugFilledBox();

        try {
            Field snippetField = RenderPipelines.class.getDeclaredField("DEBUG_FILLED_SNIPPET");
            snippetField.setAccessible(true);
            RenderPipeline.Snippet snippet = (RenderPipeline.Snippet) snippetField.get(null);

            RenderPipeline pipeline = RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath(NovaUtils.MOD_ID, "pipeline/esp_filled"))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();

            RenderSetup setup = RenderSetup.builder(pipeline).createRenderSetup();

            Method create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            create.setAccessible(true);
            cached = (RenderType) create.invoke(null, "novautils_esp_filled", setup);
            NovaUtils.LOG.info("ESP no-depth render type created");
            return cached;
        } catch (Throwable t) {
            failed = true;
            NovaUtils.LOG.error("Could not create no-depth render type, ESP will be occluded by walls", t);
            return RenderTypes.debugFilledBox();
        }
    }

    // ---- chams: the entity's own model again, through walls ----

    private static RenderPipeline chamsPipeline;
    private static final java.util.Map<Identifier, RenderType> CHAMS = new java.util.HashMap<>();
    private static boolean chamsFailed;

    /**
     * Vanilla's translucent entity render type, except the depth test always passes: the model
     * drawn with it shows through walls. One per texture (the mob's own, or plain white for a
     * solid colour). Null if this version's internals don't allow it.
     */
    public static RenderType chams(Identifier texture) {
        if (chamsFailed) return null;
        RenderType cachedType = CHAMS.get(texture);
        if (cachedType != null) return cachedType;
        try {
            if (chamsPipeline == null) {
                Field snippetField = RenderPipelines.class.getDeclaredField("ENTITY_SNIPPET");
                snippetField.setAccessible(true);
                RenderPipeline.Snippet snippet = (RenderPipeline.Snippet) snippetField.get(null);
                chamsPipeline = RenderPipeline.builder(snippet)
                    .withLocation(Identifier.fromNamespaceAndPath(NovaUtils.MOD_ID, "pipeline/chams"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
                    .withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(com.mojang.renderpearl.api.pipeline.BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(com.mojang.renderpearl.api.pipeline.CompareOp.ALWAYS_PASS, false))
                    .build();
            }
            RenderSetup setup = RenderSetup.builder(chamsPipeline)
                .withTexture("Sampler0", texture).useLightmap().useOverlay().createRenderSetup();
            Method create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            create.setAccessible(true);
            RenderType type = (RenderType) create.invoke(null, "novautils_chams_" + CHAMS.size(), setup);
            CHAMS.put(texture, type);
            return type;
        } catch (Throwable t) {
            chamsFailed = true;
            NovaUtils.LOG.error("Could not create the chams render type, chams falls back to glow", t);
            return null;
        }
    }
}
