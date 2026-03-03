package org.citydb.io.ifc.converter;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.*;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class StoreyConverter {

    private static final Logger logger = LoggerFactory.getLogger(StoreyConverter.class);

    private final IfcElementHelper helper;
    private final Set<IfcProduct> currentElements;
    private final Set<IfcProduct> exportedElements;
    private final Map<IfcProduct, String> elementObjectIds;

    public StoreyConverter(IfcElementHelper helper,
                           Set<IfcProduct> currentElements,
                           Set<IfcProduct> exportedElements,
                           Map<IfcProduct, String> elementObjectIds) {
        this.helper = helper;
        this.currentElements = currentElements;
        this.exportedElements = exportedElements;
        this.elementObjectIds = elementObjectIds;
    }

    public void convert(Feature building, IfcModelInterface model) {
        List<IfcBuildingStorey> storeys = new ArrayList<>(model.getAll(IfcBuildingStorey.class));
        storeys.removeIf(s -> !currentElements.contains(s));
        logger.info("IfcBuildingStorey: processing {} storeys", storeys.size());

        for (IfcBuildingStorey storey : storeys) {
            Feature cityStorey = Feature.of(Name.of("Storey", Namespaces.BUILDING));
            helper.initializeFeature(cityStorey, storey);

            Set<IfcProduct> storeyElements = getStoreyElements(storey);

            int xlinkCount = 0;
            for (IfcProduct element : storeyElements) {
                if (!exportedElements.contains(element)) continue;
                String objectId = elementObjectIds.get(element);
                if (objectId == null) continue;

                String propertyName;
                if (element instanceof IfcSpace) {
                    propertyName = "buildingRoom";
                } else {
                    propertyName = "buildingConstructiveElement";
                }

                cityStorey.addFeature(FeatureProperty.of(
                        Name.of(propertyName, Namespaces.BUILDING),
                        objectId,
                        RelationType.RELATES
                ));
                xlinkCount++;
            }

            building.addFeature(FeatureProperty.of(
                    Name.of("buildingSubdivision", Namespaces.BUILDING),
                    cityStorey,
                    RelationType.CONTAINS
            ));
            logger.info("Added storey '{}' with {} xlinks", storey.getName(), xlinkCount);
        }
    }

    private Set<IfcProduct> getStoreyElements(IfcBuildingStorey storey) {
        Set<IfcProduct> elements = new LinkedHashSet<>();
        collectStoreyElements(storey, elements);
        return elements;
    }

    private void collectStoreyElements(IfcObjectDefinition root, Set<IfcProduct> elements) {
        if (root instanceof IfcSpatialStructureElement spatial) {
            if (spatial.isSetContainsElements()) {
                for (IfcRelContainedInSpatialStructure rel : spatial.getContainsElements()) {
                    for (IfcProduct product : rel.getRelatedElements()) {
                        elements.add(product);
                        collectStoreyElements(product, elements);
                    }
                }
            }
        }

        if (root.isSetIsDecomposedBy()) {
            for (IfcRelAggregates rel : root.getIsDecomposedBy()) {
                for (IfcObjectDefinition obj : rel.getRelatedObjects()) {
                    if (obj instanceof IfcProduct) {
                        elements.add((IfcProduct) obj);
                    }
                    collectStoreyElements(obj, elements);
                }
            }
        }
    }
}
