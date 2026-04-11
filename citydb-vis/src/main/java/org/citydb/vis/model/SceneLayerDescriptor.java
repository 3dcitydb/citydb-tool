/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.encoder.I3SAttributeEncoder;
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
                                          List<I3SAttributeEncoder.AttrField> attrFields,
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

    private static List<Field> buildFields(List<I3SAttributeEncoder.AttrField> attrFields) {
        List<Field> fields = new ArrayList<>(attrFields.size());
        for (I3SAttributeEncoder.AttrField field : attrFields) {
            fields.add(Field.of(field));
        }
        return fields;
    }

    private static List<AttributeStorageInfo> buildAttributeStorageInfo(
            List<I3SAttributeEncoder.AttrField> attrFields) {
        List<AttributeStorageInfo> infos = new ArrayList<>(attrFields.size());
        for (int i = 0; i < attrFields.size(); i++) {
            infos.add(AttributeStorageInfo.of(i, attrFields.get(i)));
        }
        return infos;
    }

    @JSONType(alphabetic = false)
    public record HeightModelInfo(String heightModel, String vertCRS, String heightUnit) {
        public static HeightModelInfo egm96Meter() {
            return new HeightModelInfo("gravity_related_height", "EGM96_Geoid", "meter");
        }
    }

    @JSONType(alphabetic = false)
    public record SpatialReference(int wkid, int latestWkid) {
        public static SpatialReference of(int wkid) {
            return new SpatialReference(wkid, wkid);
        }
    }

    @JSONType(alphabetic = false)
    public record FullExtent(double xmin, double ymin, double xmax, double ymax,
                             double zmin, double zmax) {
        public static FullExtent from(double[] extent) {
            if (extent == null) {
                return null;
            }
            return new FullExtent(extent[0], extent[1], extent[3], extent[4], extent[2], extent[5]);
        }
    }

    @JSONType(alphabetic = false)
    public record Field(String name, String type, String alias) {
        public static Field of(I3SAttributeEncoder.AttrField field) {
            String esriType = switch (field.type()) {
                case INT -> "esriFieldTypeInteger";
                case DOUBLE -> "esriFieldTypeDouble";
                case STRING -> "esriFieldTypeString";
            };
            return new Field(field.name(), esriType, field.name());
        }
    }

    @JSONType(alphabetic = false)
    public record NodePagesInfo(int nodesPerPage, String lodSelectionMetricType) {
        public static NodePagesInfo defaults() {
            return new NodePagesInfo(I3SConstants.NODES_PER_PAGE, "maxScreenThresholdSQ");
        }
    }
}
