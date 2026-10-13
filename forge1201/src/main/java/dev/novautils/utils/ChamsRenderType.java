package dev.novautils.utils;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Esp CHAMS on 1.20.1: vanilla's translucent entity render type with the depth test switched off,
 * so a model drawn with it shows through walls. Extends RenderType only to reach its protected
 * state shards; never instantiated.
 */
public final class ChamsRenderType extends RenderType {
    private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

    private ChamsRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                            boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType chams(ResourceLocation texture) {
        return CACHE.computeIfAbsent(texture, t -> create("novautils_chams", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, true, true, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(t, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false)));
    }
}
