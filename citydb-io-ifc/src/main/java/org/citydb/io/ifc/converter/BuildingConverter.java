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

public class BuildingConverter {

    private static final Logger logger = LoggerFactory.getLogger(BuildingConverter.class);

    private final IfcElementHelper helper;
    private final boolean noStoreys;
    private final boolean listUnmappedDoorsAndWindows;
    private final boolean unrelatedDoorsAndWindowsInDummyBce;

    public BuildingConverter(IfcElementHelper helper, boolean noStoreys,
                             boolean listUnmappedDoorsAndWindows,
                             boolean unrelatedDoorsAndWindowsInDummyBce) {
        this.helper = helper;
        this.noStoreys = noStoreys;
        this.listUnmappedDoorsAndWindows = listUnmappedDoorsAndWindows;
        this.unrelatedDoorsAndWindowsInDummyBce = unrelatedDoorsAndWindowsInDummyBce;
    }

    public List<Feature> convertAll(IfcModelInterface model) {
        List<IfcBuilding> buildings = model.getAll(IfcBuilding.class);

        if (buildings.isEmpty()) {
            logger.warn("No IfcBuilding objects found in the model.");
            return Collections.emptyList();
        }

        List<Feature> result = new ArrayList<>();
        for (IfcBuilding ifcBuilding : buildings) {
            result.add(convertBuilding(ifcBuilding, model));
        }
        return result;
    }

    private Feature convertBuilding(IfcBuilding ifcBuilding, IfcModelInterface model) {
        logger.info("Converting building: {}",
                ifcBuilding.getName() != null ? ifcBuilding.getName() : "Unnamed");

        Set<IfcProduct> currentElements = getContainedElements(ifcBuilding);
        logger.info("Building decomposition: {} elements", currentElements.size());

        Set<IfcProduct> exportedElements = new HashSet<>();
        Map<IfcProduct, String> elementObjectIds = new HashMap<>();

        FillingConverter fillingConverter = new FillingConverter(helper, exportedElements);
        ConstructiveElementConverter constructiveConverter = new ConstructiveElementConverter(
                helper, fillingConverter, currentElements, exportedElements, elementObjectIds);
        RoomConverter roomConverter = new RoomConverter(
                helper, currentElements, exportedElements, elementObjectIds);
        FurnitureConverter furnitureConverter = new FurnitureConverter(helper, currentElements, exportedElements);
        InstallationConverter installationConverter = new InstallationConverter(
                helper, currentElements, exportedElements, elementObjectIds);

        Feature building = Feature.of(Name.of("Building", Namespaces.BUILDING));
        String objectId = helper.initializeFeature(building, ifcBuilding);
        elementObjectIds.put(ifcBuilding, objectId);

        // Walls (with special IfcWallStandardCase handling)
        convertWalls(constructiveConverter, building, model);

        // Other constructive elements
        convertConstructive(constructiveConverter, building, model, IfcSlab.class, "IfcSlab");
        convertConstructive(constructiveConverter, building, model, IfcRoof.class, "IfcRoof");
        convertConstructive(constructiveConverter, building, model, IfcBeam.class, "IfcBeam");
        convertConstructive(constructiveConverter, building, model, IfcColumn.class, "IfcColumn");
        convertConstructive(constructiveConverter, building, model, IfcStair.class, "IfcStair");
        convertConstructive(constructiveConverter, building, model, IfcStairFlight.class, "IfcStairFlight");
        convertConstructive(constructiveConverter, building, model, IfcRamp.class, "IfcRamp");
        convertConstructive(constructiveConverter, building, model, IfcRampFlight.class, "IfcRampFlight");
        convertConstructive(constructiveConverter, building, model, IfcCurtainWall.class, "IfcCurtainWall");
        convertConstructive(constructiveConverter, building, model, IfcPlate.class, "IfcPlate");
        convertConstructive(constructiveConverter, building, model, IfcMember.class, "IfcMember");
        convertConstructive(constructiveConverter, building, model, IfcFooting.class, "IfcFooting");
        convertConstructive(constructiveConverter, building, model, IfcPile.class, "IfcPile");
        convertConstructive(constructiveConverter, building, model, IfcBuildingElementProxy.class, "IfcBuildingElementProxy");

        // Installations
        convertInstallation(installationConverter, building, model, IfcCovering.class, "IfcCovering");
        convertInstallation(installationConverter, building, model, IfcRailing.class, "IfcRailing");

        // Rooms
        convertRooms(roomConverter, building, model);

        // Furniture
        convertFurniture(furnitureConverter, building, model);

        // Post-processing: unmapped doors and windows
        if (listUnmappedDoorsAndWindows) {
            listUnmappedDoorsAndWindows(model, currentElements, exportedElements);
        }

        if (unrelatedDoorsAndWindowsInDummyBce) {
            handleUnrelatedDoorsAndWindowsInDummyBce(building, model,
                    currentElements, exportedElements, fillingConverter, elementObjectIds);
        }

        // Storeys
        if (!noStoreys) {
            StoreyConverter storeyConverter = new StoreyConverter(
                    helper, currentElements, exportedElements, elementObjectIds);
            storeyConverter.convert(building, model);
        }

        return building;
    }

    private void convertWalls(ConstructiveElementConverter converter,
                              Feature building, IfcModelInterface model) {
        converter.convertWalls(building, model);
    }

    private <T extends IfcProduct> void convertConstructive(
            ConstructiveElementConverter converter, Feature building, IfcModelInterface model,
            Class<T> ifcClass, String className) {
        converter.convertElements(building, model, ifcClass, className);
    }

    private <T extends IfcProduct> void convertInstallation(
            InstallationConverter converter, Feature building, IfcModelInterface model,
            Class<T> ifcClass, String className) {
        converter.convert(building, model, ifcClass, className);
    }

    private void convertRooms(RoomConverter converter, Feature building,
                               IfcModelInterface model) {
        converter.convert(building, model);
    }

    private void convertFurniture(FurnitureConverter converter, Feature building,
                                   IfcModelInterface model) {
        converter.convert(building, model);
    }

    private void listUnmappedDoorsAndWindows(IfcModelInterface model,
                                               Set<IfcProduct> currentElements,
                                               Set<IfcProduct> exportedElements) {
        logger.info("=== Unmapped Doors and Windows ===");
        int count = 0;

        for (IfcDoor door : model.getAll(IfcDoor.class)) {
            if (currentElements.contains(door) && !exportedElements.contains(door)) {
                count++;
                logger.info("Unmapped Door: {} (GlobalId: {}, Name: {})",
                        door.getClass().getSimpleName(), door.getGlobalId(), door.getName());
            }
        }

        for (IfcWindow window : model.getAll(IfcWindow.class)) {
            if (currentElements.contains(window) && !exportedElements.contains(window)) {
                count++;
                logger.info("Unmapped Window: {} (GlobalId: {}, Name: {})",
                        window.getClass().getSimpleName(), window.getGlobalId(), window.getName());
            }
        }

        logger.info("Total unmapped doors/windows: {}", count);
    }

    private void handleUnrelatedDoorsAndWindowsInDummyBce(
            Feature building, IfcModelInterface model,
            Set<IfcProduct> currentElements, Set<IfcProduct> exportedElements,
            FillingConverter fillingConverter, Map<IfcProduct, String> elementObjectIds) {
        List<IfcElement> unmapped = new ArrayList<>();

        for (IfcDoor door : model.getAll(IfcDoor.class)) {
            if (currentElements.contains(door) && !exportedElements.contains(door)) {
                unmapped.add(door);
            }
        }

        for (IfcWindow window : model.getAll(IfcWindow.class)) {
            if (currentElements.contains(window) && !exportedElements.contains(window)) {
                unmapped.add(window);
            }
        }

        if (unmapped.isEmpty()) return;

        Feature dummyBce = Feature.of(Name.of("BuildingConstructiveElement", Namespaces.BUILDING));
        String objectId = "UUID_" + UUID.randomUUID();
        dummyBce.setObjectId(objectId);
        dummyBce.addAttribute(Attribute.of(Name.of("name", Namespaces.CORE), DataType.STRING)
                .setStringValue("Stub Element for unrelated Doors and Windows"));
        dummyBce.addAttribute(Attribute.of(Name.of("class", Namespaces.BUILDING), DataType.STRING)
                .setStringValue("DummyBuildingConstructiveElement"));

        for (IfcElement element : unmapped) {
            if (element instanceof IfcWindow window) {
                fillingConverter.addWindowFilling(dummyBce, window);
            } else if (element instanceof IfcDoor door) {
                fillingConverter.addDoorFilling(dummyBce, door);
            }
        }

        building.addFeature(FeatureProperty.of(
                Name.of("buildingConstructiveElement", Namespaces.BUILDING),
                dummyBce,
                RelationType.CONTAINS
        ));

        logger.info("Created dummy BCE with {} unrelated doors/windows", unmapped.size());
    }

    static Set<IfcProduct> getContainedElements(IfcObjectDefinition root) {
        Set<IfcProduct> result = new HashSet<>();
        if (root instanceof IfcProduct product) result.add(product);
        if (root.isSetIsDecomposedBy()) {
            for (IfcRelAggregates rel : root.getIsDecomposedBy()) {
                for (IfcObjectDefinition child : rel.getRelatedObjects()) {
                    result.addAll(getContainedElements(child));
                }
            }
        }
        if (root instanceof IfcSpatialStructureElement spatial) {
            if (spatial.isSetContainsElements()) {
                for (IfcRelContainedInSpatialStructure rel : spatial.getContainsElements()) {
                    for (IfcProduct product : rel.getRelatedElements()) {
                        if (product != null) {
                            result.add(product);
                            result.addAll(getContainedElements(product));
                        }
                    }
                }
            }
        }
        return result;
    }
}
