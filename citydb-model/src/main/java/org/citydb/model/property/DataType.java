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

package org.citydb.model.property;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public enum DataType implements DataTypeProvider {
    ADDRESS_PROPERTY("AddressProperty", Namespaces.CORE),
    APPEARANCE_PROPERTY("AppearanceProperty", Namespaces.CORE),
    BOOLEAN("Boolean", Namespaces.CORE),
    CITY_OBJECT_RELATION("CityObjectRelation", Namespaces.CORE),
    CODE("Code", Namespaces.CORE),
    CONSTRUCTION_EVENT("ConstructionEvent", Namespaces.CONSTRUCTION),
    DOUBLE("Double", Namespaces.CORE),
    DURATION("Duration", Namespaces.CORE),
    ELEVATION("Elevation", Namespaces.CONSTRUCTION),
    EXTERNAL_REFERENCE("ExternalReference", Namespaces.CORE),
    FEATURE_PROPERTY("FeatureProperty", Namespaces.CORE),
    GENERIC_ATTRIBUTE_SET("GenericAttributeSet", Namespaces.GENERICS),
    GEOMETRY_PROPERTY("GeometryProperty", Namespaces.CORE),
    HEIGHT("Height", Namespaces.CONSTRUCTION),
    IMPLICIT_GEOMETRY_PROPERTY("ImplicitGeometryProperty", Namespaces.CORE),
    INTEGER("Integer", Namespaces.CORE),
    MEASURE("Measure", Namespaces.CORE),
    MEASURE_OR_NIL_REASON_LIST("MeasureOrNilReasonList", Namespaces.CORE),
    OCCUPANCY("Occupancy", Namespaces.CORE),
    QUALIFIED_AREA("QualifiedArea", Namespaces.CORE),
    QUALIFIED_VOLUME("QualifiedVolume", Namespaces.CORE),
    REFERENCE("Reference", Namespaces.CORE),
    ROLE("Role", Namespaces.CITY_OBJECT_GROUP),
    ROOM_HEIGHT("RoomHeight", Namespaces.BUILDING),
    SENSOR_CONNECTION("SensorConnection", Namespaces.DYNAMIZER),
    STRING("String", Namespaces.CORE),
    STRING_OR_REF("StringOrRef", Namespaces.CORE),
    TIMESERIES_COMPONENT("TimeseriesComponent", Namespaces.DYNAMIZER),
    TIMESTAMP("Timestamp", Namespaces.CORE),
    TIME_PAIR_VALUE("TimePairValue", Namespaces.DYNAMIZER),
    TIME_POSITION("TimePosition", Namespaces.CORE),
    TRANSACTION("Transaction", Namespaces.VERSIONING),
    URI("URI", Namespaces.CORE);

    private final static Map<Name, DataType> types = new HashMap<>();
    private final Name name;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.name, type));
    }

    DataType(String localName, String namespace) {
        this.name = Name.of(localName, namespace);
    }

    public static DataType of(Name name) {
        return name != null ? types.get(name) : null;
    }

    public static DataType of(String name, String namespace) {
        return of(Name.of(name, namespace));
    }

    @Override
    public Name getName() {
        return name;
    }
}
