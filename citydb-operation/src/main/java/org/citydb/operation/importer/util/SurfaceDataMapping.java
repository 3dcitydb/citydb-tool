/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import com.alibaba.fastjson2.JSONObject;

public class SurfaceDataMapping {
    private JSONObject textureMapping;
    private JSONObject materialMapping;
    private JSONObject worldToTextureMapping;
    private JSONObject georeferencedTextureMapping;

    JSONObject getOrCreateTextureMapping() {
        if (textureMapping == null) {
            textureMapping = new JSONObject();
        }

        return textureMapping;
    }

    public boolean hasTextureMapping() {
        return textureMapping != null && !textureMapping.isEmpty();
    }

    public JSONObject getTextureMapping() {
        return textureMapping;
    }

    JSONObject getOrCreateMaterialMapping() {
        if (materialMapping == null) {
            materialMapping = new JSONObject();
        }

        return materialMapping;
    }

    public boolean hasMaterialMapping() {
        return materialMapping != null && !materialMapping.isEmpty();
    }

    public JSONObject getMaterialMapping() {
        return materialMapping;
    }

    JSONObject getOrCreateWorldToTextureMapping() {
        if (worldToTextureMapping == null) {
            worldToTextureMapping = new JSONObject();
        }

        return worldToTextureMapping;
    }

    public boolean hasWorldToTextureMapping() {
        return worldToTextureMapping != null && !worldToTextureMapping.isEmpty();
    }

    public JSONObject getWorldToTextureMapping() {
        return worldToTextureMapping;
    }

    JSONObject getOrCreateGeoreferencedTextureMapping() {
        if (georeferencedTextureMapping == null) {
            georeferencedTextureMapping = new JSONObject();
        }

        return georeferencedTextureMapping;
    }

    public boolean hasGeoreferencedTextureMapping() {
        return georeferencedTextureMapping != null && !georeferencedTextureMapping.isEmpty();
    }

    public JSONObject getGeoreferencedTextureMapping() {
        return georeferencedTextureMapping;
    }
}
