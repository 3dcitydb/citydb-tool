/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.model.feature;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum FeatureType implements FeatureTypeProvider {
    ADDRESS("Address", Namespaces.CORE),
    AUXILIARY_TRAFFIC_AREA("AuxiliaryTrafficArea", Namespaces.TRANSPORTATION),
    AUXILIARY_TRAFFIC_SPACE("AuxiliaryTrafficSpace", Namespaces.TRANSPORTATION),
    BREAKLINE_REFLIEF("BreaklineRelief", Namespaces.RELIEF),
    BRIDGE("Bridge", Namespaces.BRIDGE),
    BRIDGE_CONSTRUCTIVE_ELEMENT("BridgeConstructiveElement", Namespaces.BRIDGE),
    BRIDGE_FURNITURE("BridgeFurniture", Namespaces.BRIDGE),
    BRIDGE_INSTALLATION("BridgeInstallation", Namespaces.BRIDGE),
    BRIDGE_PART("BridgePart", Namespaces.BRIDGE),
    BRIDGE_ROOM("BridgeRoom", Namespaces.BRIDGE),
    BUILDING("Building", Namespaces.BUILDING),
    BUILDING_CONSTRUCTIVE_ELEMENT("BuildingConstructiveElement", Namespaces.BUILDING),
    BUILDING_FURNITURE("BuildingFurniture", Namespaces.BUILDING),
    BUILDING_INSTALLATION("BuildingInstallation", Namespaces.BUILDING),
    BUILDING_PART("BuildingPart", Namespaces.BUILDING),
    BUILDING_ROOM("BuildingRoom", Namespaces.BUILDING),
    BUILDING_UNIT("BuildingUnit", Namespaces.BUILDING),
    CEILING_SURFACE("CeilingSurface", Namespaces.CONSTRUCTION),
    CITY_FURNITURE("CityFurniture", Namespaces.CITY_FURNITURE),
    CITY_MODEL("CityModel", Namespaces.CORE),
    CITY_OBJECT_GROUP("CityObjectGroup", Namespaces.CITY_OBJECT_GROUP),
    CLEARANCE_SPACE("ClearanceSpace", Namespaces.TRANSPORTATION),
    CLOSURE_SURFACE("ClosureSurface", Namespaces.CORE),
    COMPOSITE_TIMESERIES("CompositeTimeseries", Namespaces.DYNAMIZER),
    DOOR("Door", Namespaces.CONSTRUCTION),
    DOOR_SURFACE("DoorSurface", Namespaces.CONSTRUCTION),
    DYNAMIZER("Dynamizer", Namespaces.DYNAMIZER),
    FLOOR_SURFACE("FloorSurface", Namespaces.CONSTRUCTION),
    GENERIC_LOGICAL_SPACE("GenericLogicalSpace", Namespaces.GENERICS),
    GENERIC_OCCUPIED_SPACE("GenericOccupiedSpace", Namespaces.GENERICS),
    GENERIC_THEMATIC_SURFACE("GenericThematicSurface", Namespaces.GENERICS),
    GENERIC_TIMESERIES("GenericTimeseries", Namespaces.DYNAMIZER),
    GENERIC_UNOCCUPIED_SPACE("GenericUnoccupiedSpace", Namespaces.GENERICS),
    GROUND_SURFACE("GroundSurface", Namespaces.CONSTRUCTION),
    HOLE("Hole", Namespaces.TRANSPORTATION),
    HOLE_SURFACE("HoleSurface", Namespaces.TRANSPORTATION),
    HOLLOW_SPACE("HollowSpace", Namespaces.TUNNEL),
    INTERIOR_WALL_SURFACE("InteriorWallSurface", Namespaces.CONSTRUCTION),
    INTERSECTION("Intersection", Namespaces.TRANSPORTATION),
    LAND_USE("LandUse", Namespaces.LAND_USE),
    MARKING("Marking", Namespaces.TRANSPORTATION),
    MASS_POINT_RELIEF("MassPointRelief", Namespaces.RELIEF),
    OTHER_CONSTRUCTION("OtherConstruction", Namespaces.CONSTRUCTION),
    OUTER_CEILING_SURFACE("OuterCeilingSurface", Namespaces.CONSTRUCTION),
    OUTER_FLOOR_SURFACE("OuterFloorSurface", Namespaces.CONSTRUCTION),
    PLANT_COVER("PlantCover", Namespaces.VEGETATION),
    POINT_CLOUD("PointCloud", Namespaces.POINT_CLOUD),
    RAILWAY("Railway", Namespaces.TRANSPORTATION),
    RASTER_RELIEF("RasterRelief", Namespaces.RELIEF),
    RELIEF_FEATURE("ReliefFeature", Namespaces.RELIEF),
    ROAD("Road", Namespaces.TRANSPORTATION),
    ROOF_SURFACE("RoofSurface", Namespaces.CONSTRUCTION),
    SECTION("Section", Namespaces.TRANSPORTATION),
    SOLITARY_VEGETATION_OBJECT("SolitaryVegetationObject", Namespaces.VEGETATION),
    SQUARE("Square", Namespaces.TRANSPORTATION),
    STANDARD_FILE_TIMESERIES("StandardFileTimeseries", Namespaces.DYNAMIZER),
    STOREY("Storey", Namespaces.BUILDING),
    TABULATED_FILE_TIMESERIES("TabulatedFileTimeseries", Namespaces.DYNAMIZER),
    TIN_RELIEF("TINRelief", Namespaces.RELIEF),
    TRACK("Track", Namespaces.TRANSPORTATION),
    TRAFFIC_AREA("TrafficArea", Namespaces.TRANSPORTATION),
    TRAFFIC_SPACE("TrafficSpace", Namespaces.TRANSPORTATION),
    TUNNEL("Tunnel", Namespaces.TUNNEL),
    TUNNEL_CONSTRUCTIVE_ELEMENT("TunnelConstructiveElement", Namespaces.TUNNEL),
    TUNNEL_FURNITURE("TunnelFurniture", Namespaces.TUNNEL),
    TUNNEL_INSTALLATION("TunnelInstallation", Namespaces.TUNNEL),
    TUNNEL_PART("TunnelPart", Namespaces.TUNNEL),
    VERSION("Version", Namespaces.VERSIONING),
    VERSION_TRANSITION("VersionTransition", Namespaces.VERSIONING),
    WALL_SURFACE("WallSurface", Namespaces.CONSTRUCTION),
    WATER_BODY("WaterBody", Namespaces.WATER_BODY),
    WATER_GROUND_SURFACE("WaterGroundSurface", Namespaces.WATER_BODY),
    WATER_SURFACE("WaterSurface", Namespaces.WATER_BODY),
    WATER_WAY("Waterway", Namespaces.TRANSPORTATION),
    WINDOW("Window", Namespaces.CONSTRUCTION),
    WINDOW_SURFACE("WindowSurface", Namespaces.CONSTRUCTION);

    private final static Map<Name, FeatureType> types = new HashMap<>();
    private final Name name;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.name, type));
    }

    FeatureType(String localName, String namespace) {
        this.name = Name.of(localName, namespace);
    }

    public static FeatureType of(Name name) {
        return name != null ? types.get(name) : null;
    }

    public static FeatureType of(String name, String namespace) {
        return of(Name.of(name, namespace));
    }

    @Override
    public Name getName() {
        return name;
    }
}
