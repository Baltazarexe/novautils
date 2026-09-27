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
}
