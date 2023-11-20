/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.model.common;

public class Namespaces {
    public static final String EMPTY_NAMESPACE = "";

    public static final String APPEARANCE = "http://www.3dcitydb.org/3dcitydb/appearance/5.0";
    public static final String BRIDGE = "http://www.3dcitydb.org/3dcitydb/bridge/5.0";
    public static final String BUILDING = "http://www.3dcitydb.org/3dcitydb/building/5.0";
    public static final String CITY_FURNITURE = "http://www.3dcitydb.org/3dcitydb/cityfurniture/5.0";
    public static final String CITY_OBJECT_GROUP = "http://www.3dcitydb.org/3dcitydb/cityobjectgroup/5.0";
    public static final String CONSTRUCTION = "http://www.3dcitydb.org/3dcitydb/construction/5.0";
    public static final String CORE = "http://www.3dcitydb.org/3dcitydb/5.0";
    public static final String DEPRECATED = "http://www.3dcitydb.org/3dcitydb/deprecated/5.0";
    public static final String DYNAMIZER = "http://www.3dcitydb.org/3dcitydb/dynamizer/5.0";
    public static final String GENERICS = "http://www.3dcitydb.org/3dcitydb/generics/5.0";
    public static final String LAND_USE = "http://www.3dcitydb.org/3dcitydb/landuse/5.0";
    public static final String POINT_CLOUD = "http://www.3dcitydb.org/3dcitydb/pointcloud/5.0";
    public static final String RELIEF = "http://www.3dcitydb.org/3dcitydb/relief/5.0";
    public static final String TRANSPORTATION = "http://www.3dcitydb.org/3dcitydb/transportation/5.0";
    public static final String TUNNEL = "http://www.3dcitydb.org/3dcitydb/tunnel/5.0";
    public static final String VEGETATION = "http://www.3dcitydb.org/3dcitydb/vegetation/5.0";
    public static final String VERSIONING = "http://www.3dcitydb.org/3dcitydb/versioning/5.0";
    public static final String WATER_BODY = "http://www.3dcitydb.org/3dcitydb/waterbody/5.0";

    public static String ensureNonNull(String namespace) {
        return namespace != null ? namespace : EMPTY_NAMESPACE;
    }

    public static boolean isCityDBNamespace(String namespace) {
        return ensureNonNull(namespace).startsWith("http://www.3dcitydb.org/3dcitydb/");
    }
}
