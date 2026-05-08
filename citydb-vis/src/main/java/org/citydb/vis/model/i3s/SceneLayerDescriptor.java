/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.citydb.vis.styling.DefaultObjectStyle;

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
    /**
     * Per-feature-type styling slots. Allocated only when
     * {@code hasStyleOverrides} is true on the scene layer. Carry baked
     * per-triangle COLOR_0 <i>plus</i> NORMAL so each surface type can
     * render with its own colour while still picking up Lambertian shading.
     * The OPAQUE / BLEND split mirrors the X3DMaterial pair: a node with
     * any styled triangle below {@code alpha = 1} routes to BLEND.
     */
    public static final int STYLED_COLORED_OPAQUE_DEFINITION_INDEX = 4;
    public static final int STYLED_COLORED_BLEND_DEFINITION_INDEX = 5;

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
    private List<StatisticsInfo> statisticsInfo;
    private PopupInfo popupInfo;
    private NodePagesInfo nodePages;

    public static SceneLayerDescriptor of(SceneLayer sceneLayer,
                                          List<AttrField> attrFields,
                                          boolean hasTextures,
                                          boolean hasColors,
                                          boolean hasStyleOverrides,
                                          boolean enableShading,
                                          DefaultObjectStyle defaultStyle) {
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
        descriptor.geometryDefinitions = buildGeometryDefinitions(hasTextures, hasColors,
                hasStyleOverrides, enableShading);

        if (hasTextures) {
            descriptor.textureSetDefinitions = List.of(TextureSetDefinition.jpeg());
        }

        descriptor.materialDefinitions = buildMaterialDefinitions(hasTextures, hasColors,
                hasStyleOverrides, defaultStyle);
        descriptor.fullExtent = FullExtent.from(sceneLayer.getExtent());
        descriptor.fields = buildFields(attrFields);
        // Our OID-typed field is named "OID" (not Esri's default "OBJECTID"),
        // so declare it explicitly at the layer level; otherwise ArcGIS may
        // fail to resolve the OID when the name doesn't match convention.
        descriptor.objectIdField = findOidFieldName(attrFields);
        descriptor.attributeStorageInfo = buildAttributeStorageInfo(attrFields);
        descriptor.statisticsInfo = buildStatisticsInfo(attrFields);
        descriptor.popupInfo = PopupInfo.of(attrFields);
        descriptor.nodePages = NodePagesInfo.defaults();
        return descriptor;
    }

    private static List<StatisticsInfo> buildStatisticsInfo(List<AttrField> attrFields) {
        List<StatisticsInfo> infos = new ArrayList<>(attrFields.size());
        for (int i = 0; i < attrFields.size(); i++) {
            infos.add(StatisticsInfo.of(i, attrFields.get(i).name()));
        }
        return infos;
    }

    private static List<GeometryDefinition> buildGeometryDefinitions(boolean hasTextures,
                                                                     boolean hasColors,
                                                                     boolean hasStyleOverrides,
                                                                     boolean enableShading) {
        // The textured slot (index 1) is filled with a placeholder untextured
        // definition when the layer has colors but no textures, so that the
        // OPAQUE/BLEND colored definitions stay at fixed indices 2/3.
        // In a pure-styling layer (overrides but no X3DMaterial) slots 2/3
        // are unreferenced placeholders held for index stability; in a
        // mixed layer they're the active X3DMaterial slots.
        // --enable-shading toggles the NORMAL attribute on every slot in
        // lock-step with the encoder; required for ArcGIS Pro / Online
        // (those clients refuse to load a layer whose legacy buffer omits
        // NORMAL).
        GeometryDefinition plain = enableShading
                ? GeometryDefinition.untextured()
                : GeometryDefinition.untexturedNoNormal();
        List<GeometryDefinition> definitions = new ArrayList<>(7);
        definitions.add(plain);
        if (hasTextures) {
            GeometryDefinition textured;
            if (enableShading) {
                textured = hasColors
                        ? GeometryDefinition.texturedColoredShaded()
                        : GeometryDefinition.texturedShaded();
            } else {
                textured = hasColors
                        ? GeometryDefinition.texturedColored()
                        : GeometryDefinition.textured();
            }
            definitions.add(textured);
        } else if (hasColors || hasStyleOverrides) {
            definitions.add(plain);
        }
        if (hasColors || hasStyleOverrides) {
            // OPAQUE and BLEND share the same buffer layout; alphaMode is
            // set on the paired MaterialDefinition, not here. With
            // --enable-shading the X3DMaterial slot picks up NORMAL via
            // the coloredShaded buffer so authored colours render PBR-shaded.
            GeometryDefinition colored = enableShading
                    ? GeometryDefinition.coloredShaded()
                    : GeometryDefinition.colored();
            definitions.add(colored);
            definitions.add(colored);
        }
        if (hasStyleOverrides) {
            GeometryDefinition styled = enableShading
                    ? GeometryDefinition.coloredShaded()
                    : GeometryDefinition.colored();
            definitions.add(styled);
            definitions.add(styled);
        }
        return definitions;
    }

    private static List<MaterialDefinition> buildMaterialDefinitions(boolean hasTextures,
                                                                    boolean hasColors,
                                                                    boolean hasStyleOverrides,
                                                                    DefaultObjectStyle defaultStyle) {
        List<MaterialDefinition> materials = new ArrayList<>(7);
        materials.add(MaterialDefinition.untextured(defaultStyle));
        if (hasTextures) {
            materials.add(MaterialDefinition.textured());
        } else if (hasColors || hasStyleOverrides) {
            materials.add(MaterialDefinition.untextured(defaultStyle));
        }
        if (hasColors || hasStyleOverrides) {
            // X3DMaterial slots — used when the layer has X3DMaterial.
            // Material is pure PBR; whether the node renders unlit or
            // shaded is decided by the paired GeometryDefinition (NORMAL
            // present iff --enable-shading). Allocated as placeholders in
            // a pure-styling layer so the styled-colored slots stay at
            // fixed indices 4/5.
            materials.add(MaterialDefinition.colored(false));
            materials.add(MaterialDefinition.colored(true));
        }
        if (hasStyleOverrides) {
            materials.add(MaterialDefinition.coloredShaded(false));
            materials.add(MaterialDefinition.coloredShaded(true));
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
