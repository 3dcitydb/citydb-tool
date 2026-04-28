/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    public static final int VERTEX_COLORED_OPAQUE_DEFINITION_INDEX = 2;
    public static final int VERTEX_COLORED_BLEND_DEFINITION_INDEX = 3;

    private int id;
    private String version;
    private String name;
    private String description;
    private String layerType;
    private List<String> capabilities;
    private HeightModelInfo heightModelInfo;
    private SpatialReference spatialReference;
    private Store store;
    private List<GeometryDefinition> geometryDefinitions;
    private List<TextureSetDefinition> textureSetDefinitions;
    private List<MaterialDefinition> materialDefinitions;
    private FullExtent fullExtent;
    private List<Field> fields;
    private String objectIdField;
    private List<AttributeStorageInfo> attributeStorageInfo;
    private PopupInfo popupInfo;
    private NodePagesInfo nodePages;

    public static SceneLayerDescriptor of(SceneLayer sceneLayer,
                                          List<AttrField> attrFields,
                                          boolean hasTextures,
                                          boolean hasColors) {
        SceneLayerDescriptor descriptor = new SceneLayerDescriptor();
        descriptor.id = 0;
        // I3S scene layer 'version' is a per-layer lifecycle UUID, NOT the
        // I3S spec version (which lives in store.version). ArcGIS Pro rejects
        // non-UUID values here and silently drops the fields array on parse,
        // breaking the identify popup even though the layer renders.
        descriptor.version = UUID.randomUUID().toString().toUpperCase();
        descriptor.name = sceneLayer.getName();
        descriptor.description = sceneLayer.getDescription();
        descriptor.layerType = SceneLayer.LAYER_TYPE;
        descriptor.capabilities = List.of("View", "Query");
        descriptor.heightModelInfo = HeightModelInfo.egm96Meter();
        descriptor.spatialReference = SpatialReference.of(sceneLayer.getWkid());
        descriptor.store = Store.of(sceneLayer, hasTextures);
        descriptor.geometryDefinitions = buildGeometryDefinitions(hasTextures, hasColors);

        if (hasTextures) {
            descriptor.textureSetDefinitions = List.of(TextureSetDefinition.jpeg());
        }

        descriptor.materialDefinitions = buildMaterialDefinitions(hasTextures, hasColors);
        descriptor.fullExtent = FullExtent.from(sceneLayer.getExtent());
        descriptor.fields = buildFields(attrFields);
        // Our OID-typed field is named "OID" (not Esri's default "OBJECTID"),
        // so declare it explicitly at the layer level; otherwise ArcGIS may
        // fail to resolve the OID when the name doesn't match convention.
        descriptor.objectIdField = findOidFieldName(attrFields);
        descriptor.attributeStorageInfo = buildAttributeStorageInfo(attrFields);
        descriptor.popupInfo = PopupInfo.of(attrFields);
        descriptor.nodePages = NodePagesInfo.defaults();
        return descriptor;
    }

    private static List<GeometryDefinition> buildGeometryDefinitions(boolean hasTextures,
                                                                     boolean hasColors) {
        // The textured slot (index 1) is filled with a placeholder untextured
        // definition when the layer has colors but no textures, so that the
        // OPAQUE/BLEND colored definitions can stay at fixed indices 2/3.
        List<GeometryDefinition> definitions = new ArrayList<>(4);
        definitions.add(GeometryDefinition.untextured());
        if (hasTextures) {
            definitions.add(hasColors
                    ? GeometryDefinition.texturedColored()
                    : GeometryDefinition.textured());
        } else if (hasColors) {
            definitions.add(GeometryDefinition.untextured());
        }
        if (hasColors) {
            // OPAQUE and BLEND share the same Draco layout; alphaMode is set
            // on the paired MaterialDefinition, not here.
            GeometryDefinition colored = GeometryDefinition.colored();
            definitions.add(colored);
            definitions.add(colored);
        }
        return definitions;
    }

    private static List<MaterialDefinition> buildMaterialDefinitions(boolean hasTextures,
                                                                    boolean hasColors) {
        List<MaterialDefinition> materials = new ArrayList<>(4);
        materials.add(MaterialDefinition.untextured());
        if (hasTextures) {
            materials.add(MaterialDefinition.textured());
        } else if (hasColors) {
            materials.add(MaterialDefinition.untextured());
        }
        if (hasColors) {
            materials.add(MaterialDefinition.colored(false));
            materials.add(MaterialDefinition.colored(true));
        }
        return materials;
    }

    private static String findOidFieldName(List<AttrField> attrFields) {
        for (AttrField f : attrFields) {
            if (f.type() == AttrType.OID) {
                return f.name();
            }
        }
        return null;
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
