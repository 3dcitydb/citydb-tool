package org.citydb.io.ifc.converter;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.IfcProduct;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InstallationConverter {

    private static final Logger logger = LoggerFactory.getLogger(InstallationConverter.class);

    private final IfcElementHelper helper;
    private final Set<IfcProduct> currentElements;
    private final Set<IfcProduct> exportedElements;
    private final Map<IfcProduct, String> elementObjectIds;

    public InstallationConverter(IfcElementHelper helper,
                                  Set<IfcProduct> currentElements,
                                  Set<IfcProduct> exportedElements,
                                  Map<IfcProduct, String> elementObjectIds) {
        this.helper = helper;
        this.currentElements = currentElements;
        this.exportedElements = exportedElements;
        this.elementObjectIds = elementObjectIds;
    }

    public <T extends IfcProduct> void convert(Feature building, IfcModelInterface model,
                                                Class<T> ifcClass, String className) {
        List<T> elements = new ArrayList<>(model.getAll(ifcClass));
        elements.removeIf(e -> !currentElements.contains(e));
        logger.info("{}: processing {} elements", className, elements.size());

        for (T element : elements) {
            Feature installation = Feature.of(Name.of("BuildingInstallation", Namespaces.BUILDING));
            String objectId = helper.initializeFeature(installation, element);
            elementObjectIds.put(element, objectId);

            boolean hasGeometry = helper.addGeometryAndAppearance(installation, element);
            if (hasGeometry) {
                exportedElements.add(element);
            }

            installation.addAttribute(Attribute.of(Name.of("class", Namespaces.BUILDING), DataType.STRING)
                    .setStringValue(className));

            building.addFeature(FeatureProperty.of(
                    Name.of("buildingInstallation", Namespaces.BUILDING),
                    installation,
                    RelationType.CONTAINS
            ));
        }
    }
}
