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

package org.citydb.database.metadata;

public class DatabaseMetadata {
    private final DatabaseVersion version;
    private final SpatialReference spatialReference;
    private final String vendorProductName;
    private final String vendorProductVersion;
    private final int vendorMajorVersion;
    private final int vendorMinorVersion;

    private DatabaseMetadata(
            DatabaseVersion version, SpatialReference spatialReference, String vendorProductName,
            String vendorProductVersion, int vendorMajorVersion, int vendorMinorVersion) {
        this.version = version;
        this.spatialReference = spatialReference;
        this.vendorProductName = vendorProductName;
        this.vendorProductVersion = vendorProductVersion;
        this.vendorMajorVersion = vendorMajorVersion;
        this.vendorMinorVersion = vendorMinorVersion;
    }

    public static DatabaseMetadata of(
            DatabaseVersion version, SpatialReference spatialReference, String vendorProductName,
            String vendorProductVersion, int vendorMajorVersion, int vendorMinorVersion) {
        return new DatabaseMetadata(version, spatialReference, vendorProductName, vendorProductVersion,
                vendorMajorVersion, vendorMinorVersion);
    }

    public DatabaseVersion getVersion() {
        return version;
    }

    public SpatialReference getSpatialReference() {
        return spatialReference;
    }

    public String getVendorProductName() {
        return vendorProductName;
    }

    public String getVendorProductVersion() {
        return vendorProductVersion;
    }

    public int getVendorMajorVersion() {
        return vendorMajorVersion;
    }

    public int getVendorMinorVersion() {
        return vendorMinorVersion;
    }
}
