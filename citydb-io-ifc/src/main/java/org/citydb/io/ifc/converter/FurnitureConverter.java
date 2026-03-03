package org.citydb.io.ifc.converter;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.IfcFurnishingElement;
import org.bimserver.models.ifc4.IfcProduct;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FurnitureConverter {

    private static final Logger logger = LoggerFactory.getLogger(FurnitureConverter.class);

    private final IfcElementHelper helper;
    private final Set<IfcProduct> currentElements;
    private final Set<IfcProduct> exportedElements;

    public FurnitureConverter(IfcElementHelper helper,
                              Set<IfcProduct> currentElements,
                              Set<IfcProduct> exportedElements) {
        this.helper = helper;
        this.currentElements = currentElements;
        this.exportedElements = exportedElements;
    }

    public void convert(Feature building, IfcModelInterface model) {
        List<IfcFurnishingElement> allFurniture = new ArrayList<>(model.getAll(IfcFurnishingElement.class));
        allFurniture.removeIf(e -> !currentElements.contains(e));
        logger.info("IfcFurnishingElement: processing {} furniture items", allFurniture.size());

        for (IfcFurnishingElement element : allFurniture) {
            Feature furniture = Feature.of(Name.of("BuildingFurniture", Namespaces.BUILDING));
            helper.initializeFeature(furniture, element);

            boolean hasGeometry = helper.addGeometryAndAppearance(furniture, element);
            if (hasGeometry) {
                exportedElements.add(element);
                building.addFeature(FeatureProperty.of(
                        Name.of("buildingFurniture", Namespaces.BUILDING),
                        furniture,
                        RelationType.CONTAINS
                ));
            }
        }
    }
}
