package org.citydb.io.ifc.converter;

import org.bimserver.models.ifc4.*;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.RelationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class FillingConverter {

    private static final Logger logger = LoggerFactory.getLogger(FillingConverter.class);

    private final IfcElementHelper helper;
    private final Set<IfcProduct> exportedElements;

    public FillingConverter(IfcElementHelper helper, Set<IfcProduct> exportedElements) {
        this.helper = helper;
        this.exportedElements = exportedElements;
    }

    public void embedFillings(Feature parentFeature, IfcElement element) {
        if (!element.isSetHasOpenings()) return;

        for (IfcRelVoidsElement relVoids : element.getHasOpenings()) {
            IfcFeatureElementSubtraction sub = relVoids.getRelatedOpeningElement();
            if (sub instanceof IfcOpeningElement opening) {
                if (opening.isSetHasFillings()) {
                    for (IfcRelFillsElement relFills : opening.getHasFillings()) {
                        IfcElement filling = relFills.getRelatedBuildingElement();
                        if (filling instanceof IfcWindow window) {
                            addFilling(parentFeature, window, "Window", Namespaces.CONSTRUCTION);
                        } else if (filling instanceof IfcDoor door) {
                            addFilling(parentFeature, door, "Door", Namespaces.CONSTRUCTION);
                        }
                    }
                }
            }
        }
    }

    public void addWindowFilling(Feature parentFeature, IfcWindow window) {
        addFilling(parentFeature, window, "Window", Namespaces.CONSTRUCTION);
    }

    public void addDoorFilling(Feature parentFeature, IfcDoor door) {
        addFilling(parentFeature, door, "Door", Namespaces.CONSTRUCTION);
    }

    private void addFilling(Feature parentFeature, IfcElement element,
                            String featureTypeName, String namespace) {
        Feature filling = Feature.of(Name.of(featureTypeName, namespace));
        helper.initializeFeature(filling, element);
        helper.addGeometryAndAppearance(filling, element);

        parentFeature.addFeature(FeatureProperty.of(
                Name.of("filling", Namespaces.CONSTRUCTION),
                filling,
                RelationType.CONTAINS
        ));

        exportedElements.add(element);
        logger.debug("Embedded {} '{}' as filling", featureTypeName, element.getName());
    }
}
