package org.citydb.io.ifc.converter;

import org.bimserver.models.ifc4.IfcProduct;
import org.citydb.io.ifc.reader.AppearanceProcessor;
import org.citydb.io.ifc.reader.GeometryProcessor;
import org.citydb.io.ifc.reader.PropertyProcessor;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.GeometryProperty;

import java.util.List;
import java.util.UUID;

public class IfcElementHelper {

    private final GeometryProcessor geometryProcessor;
    private final PropertyProcessor propertyProcessor;
    private final AppearanceProcessor appearanceProcessor;
    private final String ifcFilename;
    private final boolean noReferences;

    public IfcElementHelper(GeometryProcessor geometryProcessor,
                            PropertyProcessor propertyProcessor,
                            AppearanceProcessor appearanceProcessor,
                            String ifcFilename,
                            boolean noReferences) {
        this.geometryProcessor = geometryProcessor;
        this.propertyProcessor = propertyProcessor;
        this.appearanceProcessor = appearanceProcessor;
        this.ifcFilename = ifcFilename;
        this.noReferences = noReferences;
    }

    public GeometryProcessor geometryProcessor() {
        return geometryProcessor;
    }

    public PropertyProcessor propertyProcessor() {
        return propertyProcessor;
    }

    public AppearanceProcessor appearanceProcessor() {
        return appearanceProcessor;
    }

    public String initializeFeature(Feature feature, IfcProduct element) {
        String objectId = "UUID_" + UUID.randomUUID();
        feature.setObjectId(objectId);

        if (element.getGlobalId() != null) {
            feature.setIdentifier(element.getGlobalId());
        }

        if (element.getName() != null) {
            feature.addAttribute(Attribute.of(Name.of("name", Namespaces.CORE), DataType.STRING)
                    .setStringValue(element.getName()));
        }

        if (element.getDescription() != null) {
            feature.addAttribute(Attribute.of(Name.of("description", Namespaces.CORE), DataType.STRING)
                    .setStringValue(element.getDescription()));
        }

        propertyProcessor.addProperties(feature, element);

        if (!noReferences && element.getGlobalId() != null && ifcFilename != null) {
            feature.addAttribute(Attribute.of(
                    Name.of("externalReference", Namespaces.CORE), DataType.EXTERNAL_REFERENCE)
                    .setURI(element.getGlobalId())
                    .setCodeSpace(ifcFilename));
        }

        return objectId;
    }

    public boolean addGeometryAndAppearance(Feature feature, IfcProduct element) {
        List<double[]> polygonCoords = geometryProcessor.getTransformedPolygons(element);
        if (polygonCoords.isEmpty()) {
            return false;
        }

        GeometryProcessor.GeometryResult result = geometryProcessor.buildGeometry(element, polygonCoords);

        feature.addGeometry(GeometryProperty.of(
                Name.of(result.geometryName(), Namespaces.CORE), result.geometry()
        ).setLod("3"));

        List<Polygon> polygons = result.polygons();
        appearanceProcessor.addAppearance(feature, element, polygons);

        return true;
    }
}
