/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
