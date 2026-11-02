package com.creatorskit.models;

import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks the unlit {@link ModelData} and lighting recipe used to build each
 * rendered {@link Model} the plugin creates.
 *
 * <p>RuneLite 1.13 removed the rendered-model merge API, so a private,
 * mutable base model has to be rebuilt from the unlit source data instead.
 * The registry is deliberately weak-keyed: Models are long-lived and may be
 * shared by several Characters, and the source should disappear with them.
 */
public final class ModelSources
{
    private static final Map<Model, Source> SOURCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ModelSources()
    {
    }

    public static final class Source
    {
        private final ModelData data;
        private final CustomLighting lighting;

        private Source(ModelData data, CustomLighting lighting)
        {
            this.data = data;
            this.lighting = lighting;
        }

        /**
         * Builds a fresh, privately owned Model from this source. The returned
         * Model's arrays are not shared with the original Model, so per-character
         * colour/vertex mutations are safe.
         */
        public Model createModel(Client client)
        {
            if (client == null || data == null || lighting == null)
            {
                return null;
            }

            ModelData copy = client.mergeModels(data);
            if (copy == null)
            {
                return null;
            }

            return copy.light(
                    lighting.getAmbient(),
                    lighting.getContrast(),
                    lighting.getX(),
                    -lighting.getZ(),
                    lighting.getY());
        }
    }

    public static void register(Model model, ModelData data, CustomLighting lighting)
    {
        if (model == null || data == null || lighting == null)
        {
            return;
        }
        SOURCES.put(model, new Source(data, lighting));
    }

    public static void register(Model model, ModelData data, LightingStyle style)
    {
        if (model == null || data == null || style == null)
        {
            return;
        }
        register(model, data, new CustomLighting(
                style.getAmbient(),
                style.getContrast(),
                style.getX(),
                style.getY(),
                style.getZ()));
    }

    public static Source get(Model model)
    {
        return model == null ? null : SOURCES.get(model);
    }

    /**
     * Equivalent to {@link Client#loadModel(int)} but records the unlit source
     * and default lighting so the model can later be rebuilt privately.
     */
    public static Model loadCacheModel(Client client, int modelId)
    {
        ModelData data = client.loadModelData(modelId);
        if (data == null)
        {
            return client.loadModel(modelId);
        }

        LightingStyle style = LightingStyle.DEFAULT;
        Model model = data.light(
                style.getAmbient(),
                style.getContrast(),
                style.getX(),
                -style.getZ(),
                style.getY());
        register(model, data, style);
        return model;
    }
}
