/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import org.citydb.database.util.Json;
import tools.jackson.databind.node.ObjectNode;

public class SurfaceDataMapping {
    private ObjectNode textureMapping;
    private ObjectNode materialMapping;
    private ObjectNode worldToTextureMapping;
    private ObjectNode georeferencedTextureMapping;

    ObjectNode getOrCreateTextureMapping() {
        if (textureMapping == null) {
            textureMapping = Json.objectNode();
        }

        return textureMapping;
    }

    public boolean hasTextureMapping() {
        return textureMapping != null && !textureMapping.isEmpty();
    }

    public ObjectNode getTextureMapping() {
        return textureMapping;
    }

    ObjectNode getOrCreateMaterialMapping() {
        if (materialMapping == null) {
            materialMapping = Json.objectNode();
        }

        return materialMapping;
    }

    public boolean hasMaterialMapping() {
        return materialMapping != null && !materialMapping.isEmpty();
    }

    public ObjectNode getMaterialMapping() {
        return materialMapping;
    }

    ObjectNode getOrCreateWorldToTextureMapping() {
        if (worldToTextureMapping == null) {
            worldToTextureMapping = Json.objectNode();
        }

        return worldToTextureMapping;
    }

    public boolean hasWorldToTextureMapping() {
        return worldToTextureMapping != null && !worldToTextureMapping.isEmpty();
    }

    public ObjectNode getWorldToTextureMapping() {
        return worldToTextureMapping;
    }

    ObjectNode getOrCreateGeoreferencedTextureMapping() {
        if (georeferencedTextureMapping == null) {
            georeferencedTextureMapping = Json.objectNode();
        }

        return georeferencedTextureMapping;
    }

    public boolean hasGeoreferencedTextureMapping() {
        return georeferencedTextureMapping != null && !georeferencedTextureMapping.isEmpty();
    }

    public ObjectNode getGeoreferencedTextureMapping() {
        return georeferencedTextureMapping;
    }
}
