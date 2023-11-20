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

package org.citydb.database.metadata;

public class SpatialReference {
    private final int srid;
    private final SpatialReferenceType type;
    private final String name;
    private final String uri;
    private final String wkt;

    private SpatialReference(int srid, SpatialReferenceType type, String name, String uri, String wkt) {
        this.srid = srid;
        this.type = type;
        this.name = name;
        this.uri = uri;
        this.wkt = wkt;
    }

    public static SpatialReference of(int srid, SpatialReferenceType type, String name, String uri, String wkt) {
        return new SpatialReference(srid, type, name, uri, wkt);
    }

    public int getSRID() {
        return srid;
    }

    public SpatialReferenceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getURI() {
        return uri;
    }

    public String getWKT() {
        return wkt;
    }
}
