/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;
import java.util.Map;

/**
 * Legacy per-node shared resource (nodes/{i}/shared/index.json).
 * CesiumJS reads this file for texture metadata in I3S 1.x compatibility mode.
 */
@JSONType(alphabetic = false)
public class SharedResource {
    private Map<String, Material> materialDefinitions;
    private Map<String, TextureDefinition> textureDefinitions;

    public static SharedResource atlas(boolean isAtlas, int imageSize) {
        SharedResource resource = new SharedResource();
        resource.materialDefinitions = Map.of("Mat0", Material.standard());
        resource.textureDefinitions = Map.of("0", TextureDefinition.jpeg(isAtlas, imageSize));
        return resource;
    }

    @JSONType(alphabetic = false)
    public static class Material {
        private String type;
        private String name;
        private Params params;

        public static Material standard() {
            Material material = new Material();
            material.type = "standard";
            material.name = "standard";
            material.params = Params.standard();
            return material;
        }
    }

    @JSONType(alphabetic = false)
    public static class Params {
        private boolean vertexColors;
        private int reflectivity;
        private int[] ambient;
        private int[] diffuse;
        private double[] specular;
        private int shininess;
        private String renderMode;
        private String cullFace;

        public static Params standard() {
            Params params = new Params();
            params.vertexColors = false;
            params.reflectivity = 0;
            params.ambient = new int[]{0, 0, 0};
            params.diffuse = new int[]{1, 1, 1};
            params.specular = new double[]{0.09803921568, 0.09803921568, 0.09803921568};
            params.shininess = 1;
            params.renderMode = "solid";
            params.cullFace = "back";
            return params;
        }
    }

    @JSONType(alphabetic = false)
    public static class TextureDefinition {
        private List<String> encoding;
        private List<String> wrap;
        private boolean atlas;
        private String uvSet;
        private String channels;
        private List<Image> images;

        public static TextureDefinition jpeg(boolean isAtlas, int imageSize) {
            TextureDefinition definition = new TextureDefinition();
            definition.encoding = List.of("image/jpeg");
            definition.wrap = List.of("none", "none");
            definition.atlas = isAtlas;
            definition.uvSet = "uv0";
            definition.channels = "rgb";
            definition.images = List.of(Image.of(imageSize));
            return definition;
        }
    }

    @JSONType(alphabetic = false)
    public record Image(String id, int size, int pixelInWorldUnits, List<String> href) {
        public static Image of(int size) {
            return new Image("0", size, 0, List.of("../textures/0"));
        }
    }
}
