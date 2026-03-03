package org.citydb.io.ifc.converter;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.*;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ConstructiveElementConverter {

    private static final Logger logger = LoggerFactory.getLogger(ConstructiveElementConverter.class);

    private final IfcElementHelper helper;
    private final FillingConverter fillingConverter;
    private final Set<IfcProduct> currentElements;
    private final Set<IfcProduct> exportedElements;
    private final Map<IfcProduct, String> elementObjectIds;

    public ConstructiveElementConverter(IfcElementHelper helper,
                                        FillingConverter fillingConverter,
                                        Set<IfcProduct> currentElements,
                                        Set<IfcProduct> exportedElements,
                                        Map<IfcProduct, String> elementObjectIds) {
        this.helper = helper;
        this.fillingConverter = fillingConverter;
        this.currentElements = currentElements;
        this.exportedElements = exportedElements;
        this.elementObjectIds = elementObjectIds;
    }

    public void convertWalls(Feature building, IfcModelInterface model) {
        List<IfcWall> walls = new ArrayList<>(model.getAll(IfcWallStandardCase.class));
        for (IfcWall w : model.getAll(IfcWall.class)) {
            if (!(w instanceof IfcWallStandardCase)) {
                walls.add(w);
            }
        }
        walls.removeIf(e -> !currentElements.contains(e));

        logger.info("IfcWall: processing {} walls", walls.size());

        for (IfcWall wall : walls) {
            Feature bce = Feature.of(Name.of("BuildingConstructiveElement", Namespaces.BUILDING));
            String objectId = helper.initializeFeature(bce, wall);
            elementObjectIds.put(wall, objectId);

            boolean hasGeometry = helper.addGeometryAndAppearance(bce, wall);
            if (hasGeometry) {
                exportedElements.add(wall);
            }

            fillingConverter.embedFillings(bce, wall);

            String wallClass = (wall instanceof IfcWallStandardCase) ? "IfcWallStandardCase" : "IfcWall";
            bce.addAttribute(Attribute.of(Name.of("class", Namespaces.BUILDING), DataType.STRING)
                    .setStringValue(wallClass));

            building.addFeature(FeatureProperty.of(
                    Name.of("buildingConstructiveElement", Namespaces.BUILDING),
                    bce,
                    RelationType.CONTAINS
            ));
        }
    }

    public <T extends IfcProduct> void convertElements(Feature building, IfcModelInterface model,
                                                        Class<T> ifcClass, String className) {
        List<T> elements = new ArrayList<>(model.getAll(ifcClass));
        elements.removeIf(e -> !currentElements.contains(e));
        logger.info("{}: processing {} elements", className, elements.size());

        for (T element : elements) {
            Feature bce = Feature.of(Name.of("BuildingConstructiveElement", Namespaces.BUILDING));
            String objectId = helper.initializeFeature(bce, element);
            elementObjectIds.put(element, objectId);

            if (element instanceof IfcElement ifcElement) {
                fillingConverter.embedFillings(bce, ifcElement);
            }

            boolean hasGeometry = helper.addGeometryAndAppearance(bce, element);
            if (hasGeometry) {
                exportedElements.add(element);
            }

            bce.addAttribute(Attribute.of(Name.of("class", Namespaces.BUILDING), DataType.STRING)
                    .setStringValue(className));

            building.addFeature(FeatureProperty.of(
                    Name.of("buildingConstructiveElement", Namespaces.BUILDING),
                    bce,
                    RelationType.CONTAINS
            ));
        }
    }
}
