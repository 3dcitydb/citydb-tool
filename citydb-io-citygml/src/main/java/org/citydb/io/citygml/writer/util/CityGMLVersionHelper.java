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

package org.citydb.io.citygml.writer.util;

import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.ade.ADERegistry;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.xml.CityGMLADELoader;
import org.citygml4j.xml.module.ade.ADEModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CityGMLVersionHelper {
    private static final Map<Name, Set<CityGMLVersion>> versionsByFeatureType = new HashMap<>();
    private final Set<Name> featureTypes;
    private final Set<String> adeNamespaceURIs;

    private CityGMLVersionHelper(Set<Name> featureTypes, Set<String> adeNamespaceURIs) {
        this.featureTypes = featureTypes;
        this.adeNamespaceURIs = adeNamespaceURIs;
    }

    static {
        put(FeatureType.ADDRESS, CityGMLVersion.values());
        put(FeatureType.AUXILIARY_TRAFFIC_AREA, CityGMLVersion.values());
        put(FeatureType.AUXILIARY_TRAFFIC_SPACE, CityGMLVersion.values());
        put(FeatureType.BREAKLINE_REFLIEF, CityGMLVersion.values());
        put(FeatureType.BRIDGE, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.BRIDGE_CONSTRUCTIVE_ELEMENT, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.BRIDGE_FURNITURE, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.BRIDGE_INSTALLATION, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.BRIDGE_PART, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.BRIDGE_ROOM, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.BUILDING, CityGMLVersion.values());
        put(FeatureType.BUILDING_CONSTRUCTIVE_ELEMENT, CityGMLVersion.v3_0);
        put(FeatureType.BUILDING_FURNITURE, CityGMLVersion.values());
        put(FeatureType.BUILDING_INSTALLATION, CityGMLVersion.values());
        put(FeatureType.BUILDING_PART, CityGMLVersion.values());
        put(FeatureType.BUILDING_ROOM, CityGMLVersion.values());
        put(FeatureType.BUILDING_UNIT, CityGMLVersion.v3_0);
        put(FeatureType.CEILING_SURFACE, CityGMLVersion.values());
        put(FeatureType.CITY_FURNITURE, CityGMLVersion.values());
        put(FeatureType.CITY_MODEL, CityGMLVersion.values());
        put(FeatureType.CITY_OBJECT_GROUP, CityGMLVersion.values());
        put(FeatureType.CLEARANCE_SPACE, CityGMLVersion.v3_0);
        put(FeatureType.CLOSURE_SURFACE, CityGMLVersion.values());
        put(FeatureType.COMPOSITE_TIMESERIES, CityGMLVersion.v3_0);
        put(FeatureType.DOOR, CityGMLVersion.v3_0);
        put(FeatureType.DOOR_SURFACE, CityGMLVersion.values());
        put(FeatureType.DYNAMIZER, CityGMLVersion.v3_0);
        put(FeatureType.FLOOR_SURFACE, CityGMLVersion.values());
        put(FeatureType.GENERIC_LOGICAL_SPACE, CityGMLVersion.v3_0);
        put(FeatureType.GENERIC_OCCUPIED_SPACE, CityGMLVersion.values());
        put(FeatureType.GENERIC_THEMATIC_SURFACE, CityGMLVersion.v3_0);
        put(FeatureType.GENERIC_TIMESERIES, CityGMLVersion.v3_0);
        put(FeatureType.GENERIC_UNOCCUPIED_SPACE, CityGMLVersion.v3_0);
        put(FeatureType.GROUND_SURFACE, CityGMLVersion.values());
        put(FeatureType.HOLE, CityGMLVersion.v3_0);
        put(FeatureType.HOLE_SURFACE, CityGMLVersion.v3_0);
        put(FeatureType.HOLLOW_SPACE, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.INTERIOR_WALL_SURFACE, CityGMLVersion.values());
        put(FeatureType.INTERSECTION, CityGMLVersion.v3_0);
        put(FeatureType.LAND_USE, CityGMLVersion.values());
        put(FeatureType.MARKING, CityGMLVersion.v3_0);
        put(FeatureType.MASS_POINT_RELIEF, CityGMLVersion.values());
        put(FeatureType.OTHER_CONSTRUCTION, CityGMLVersion.v3_0);
        put(FeatureType.OUTER_CEILING_SURFACE, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.OUTER_FLOOR_SURFACE, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.PLANT_COVER, CityGMLVersion.values());
        put(FeatureType.POINT_CLOUD, CityGMLVersion.v3_0);
        put(FeatureType.RAILWAY, CityGMLVersion.values());
        put(FeatureType.RASTER_RELIEF, CityGMLVersion.values());
        put(FeatureType.RELIEF_FEATURE, CityGMLVersion.values());
        put(FeatureType.ROAD, CityGMLVersion.values());
        put(FeatureType.ROOF_SURFACE, CityGMLVersion.values());
        put(FeatureType.SECTION, CityGMLVersion.v3_0);
        put(FeatureType.SOLITARY_VEGETATION_OBJECT, CityGMLVersion.values());
        put(FeatureType.SQUARE, CityGMLVersion.values());
        put(FeatureType.STANDARD_FILE_TIMESERIES, CityGMLVersion.v3_0);
        put(FeatureType.STOREY, CityGMLVersion.v3_0);
        put(FeatureType.TABULATED_FILE_TIMESERIES, CityGMLVersion.v3_0);
        put(FeatureType.TIN_RELIEF, CityGMLVersion.values());
        put(FeatureType.TRACK, CityGMLVersion.values());
        put(FeatureType.TRAFFIC_AREA, CityGMLVersion.values());
        put(FeatureType.TRAFFIC_SPACE, CityGMLVersion.values());
        put(FeatureType.TUNNEL, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.TUNNEL_CONSTRUCTIVE_ELEMENT, CityGMLVersion.v3_0);
        put(FeatureType.TUNNEL_FURNITURE, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.TUNNEL_INSTALLATION, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.TUNNEL_PART, CityGMLVersion.v3_0, CityGMLVersion.v2_0);
        put(FeatureType.VERSION, CityGMLVersion.v3_0);
        put(FeatureType.VERSION_TRANSITION, CityGMLVersion.v3_0);
        put(FeatureType.WALL_SURFACE, CityGMLVersion.values());
        put(FeatureType.WATER_BODY, CityGMLVersion.values());
        put(FeatureType.WATER_GROUND_SURFACE, CityGMLVersion.values());
        put(FeatureType.WATER_SURFACE, CityGMLVersion.values());
        put(FeatureType.WATER_WAY, CityGMLVersion.v3_0);
        put(FeatureType.WINDOW, CityGMLVersion.v3_0);
        put(FeatureType.WINDOW_SURFACE, CityGMLVersion.values());
    }
    
    private static void put(FeatureType featureType, CityGMLVersion... versions) {
        versionsByFeatureType.put(featureType.getName(), Set.of(versions));
    }

    public static CityGMLVersionHelper of(CityGMLVersion version) {
        Set<Name> featureTypes = versionsByFeatureType.entrySet().stream()
                .filter(e -> e.getValue().contains(version))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<String> adeNamespaceURIs = ADERegistry.getInstance().getADELoader(CityGMLADELoader.class)
                .getADEModules(version).stream()
                .map(ADEModule::getNamespaceURI)
                .collect(Collectors.toSet());

        return new CityGMLVersionHelper(featureTypes, adeNamespaceURIs);
    }

    public boolean isSupported(Feature feature) {
        return featureTypes.contains(feature.getFeatureType())
                || adeNamespaceURIs.contains(feature.getFeatureType().getNamespace());
    }
}
