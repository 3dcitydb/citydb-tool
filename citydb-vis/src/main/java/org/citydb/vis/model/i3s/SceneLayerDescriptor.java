/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.scene.SceneLayer;

import java.util.ArrayList;
import java.util.List;

@JSONType(alphabetic = false)
public class SceneLayerDescriptor {
    /**
     * Indices into {@link #geometryDefinitions} / {@link #materialDefinitions}.
     * Node pages reference these by index, so the list order in
     * {@link #buildGeometryDefinitions} and {@link #buildMaterialDefinitions}
     * must match.
     */
    public static final int UNTEXTURED_DEFINITION_INDEX = 0;
    public static final int TEXTURED_DEFINITION_INDEX = 1;

    private int id;
    private String version;
    private String name;
    private String description;
    private String layerType;
    private HeightModelInfo heightModelInfo;
    private SpatialReference spatialReference;
    private Store store;
    private List<GeometryDefinition> geometryDefinitions;
    private List<TextureSetDefinition> textureSetDefinitions;
    private List<MaterialDefinition> materialDefinitions;
    private FullExtent fullExtent;
    private List<Field> fields;
    private List<AttributeStorageInfo> attributeStorageInfo;
    private NodePagesInfo nodePages;

    public static SceneLayerDescriptor of(SceneLayer sceneLayer,
                                          List<AttrField> attrFields,
                                          boolean hasTextures) {
        SceneLayerDescriptor descriptor = new SceneLayerDescriptor();
        descriptor.id = 0;
        descriptor.version = SceneLayer.I3S_VERSION;
        descriptor.name = sceneLayer.getName();
        descriptor.description = sceneLayer.getDescription();
        descriptor.layerType = SceneLayer.LAYER_TYPE;
        descriptor.heightModelInfo = HeightModelInfo.egm96Meter();
        descriptor.spatialReference = SpatialReference.of(sceneLayer.getWkid());
        descriptor.store = Store.of(sceneLayer, hasTextures);
        descriptor.geometryDefinitions = buildGeometryDefinitions(hasTextures);

        if (hasTextures) {
            descriptor.textureSetDefinitions = List.of(TextureSetDefinition.jpeg());
        }

        descriptor.materialDefinitions = buildMaterialDefinitions(hasTextures);
        descriptor.fullExtent = FullExtent.from(sceneLayer.getExtent());
        descriptor.fields = buildFields(attrFields);
        descriptor.attributeStorageInfo = buildAttributeStorageInfo(attrFields);
        descriptor.nodePages = NodePagesInfo.defaults();
        return descriptor;
    }

    private static List<GeometryDefinition> buildGeometryDefinitions(boolean hasTextures) {
        List<GeometryDefinition> definitions = new ArrayList<>(2);
        definitions.add(GeometryDefinition.untextured());
        if (hasTextures) {
            definitions.add(GeometryDefinition.textured());
        }
        return definitions;
    }

    private static List<MaterialDefinition> buildMaterialDefinitions(boolean hasTextures) {
        List<MaterialDefinition> materials = new ArrayList<>(2);
        materials.add(MaterialDefinition.untextured());
        if (hasTextures) {
            materials.add(MaterialDefinition.textured());
        }
        return materials;
    }

    private static List<Field> buildFields(List<AttrField> attrFields) {
        List<Field> fields = new ArrayList<>(attrFields.size());
        for (AttrField field : attrFields) {
            fields.add(Field.of(field));
        }
        return fields;
    }

    private static List<AttributeStorageInfo> buildAttributeStorageInfo(
            List<AttrField> attrFields) {
        List<AttributeStorageInfo> infos = new ArrayList<>(attrFields.size());
        for (int i = 0; i < attrFields.size(); i++) {
            infos.add(AttributeStorageInfo.of(i, attrFields.get(i)));
        }
        return infos;
    }
}
