package org.citydb.io.ifc.converter;

import org.bimserver.emf.IfcModelInterface;
import org.bimserver.models.ifc4.IfcProduct;
import org.bimserver.models.ifc4.IfcSpace;
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

public class RoomConverter {

    private static final Logger logger = LoggerFactory.getLogger(RoomConverter.class);

    private final IfcElementHelper helper;
    private final Set<IfcProduct> currentElements;
    private final Set<IfcProduct> exportedElements;
    private final Map<IfcProduct, String> elementObjectIds;

    public RoomConverter(IfcElementHelper helper,
                         Set<IfcProduct> currentElements,
                         Set<IfcProduct> exportedElements,
                         Map<IfcProduct, String> elementObjectIds) {
        this.helper = helper;
        this.currentElements = currentElements;
        this.exportedElements = exportedElements;
        this.elementObjectIds = elementObjectIds;
    }

    public void convert(Feature building, IfcModelInterface model) {
        List<IfcSpace> spaces = new ArrayList<>(model.getAll(IfcSpace.class));
        spaces.removeIf(e -> !currentElements.contains(e));
        logger.info("IfcSpace: processing {} rooms", spaces.size());

        for (IfcSpace space : spaces) {
            Feature room = Feature.of(Name.of("BuildingRoom", Namespaces.BUILDING));
            String objectId = helper.initializeFeature(room, space);
            elementObjectIds.put(space, objectId);

            boolean hasGeometry = helper.addGeometryAndAppearance(room, space);
            if (hasGeometry) {
                exportedElements.add(space);
            }

            room.addAttribute(Attribute.of(Name.of("class", Namespaces.BUILDING), DataType.STRING)
                    .setStringValue("IfcSpace"));

            building.addFeature(FeatureProperty.of(
                    Name.of("buildingRoom", Namespaces.BUILDING),
                    room,
                    RelationType.CONTAINS
            ));
        }
    }
}
