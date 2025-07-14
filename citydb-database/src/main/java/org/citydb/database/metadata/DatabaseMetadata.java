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

import org.citydb.core.version.Version;
import org.citydb.database.srs.SpatialReference;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

public class DatabaseMetadata {
    private final Version version;
    private final SpatialReference spatialReference;
    private final boolean changelogEnabled;
    private final String vendorProductName;
    private final String vendorProductVersion;
    private final int vendorMajorVersion;
    private final int vendorMinorVersion;

    private DatabaseMetadata(
            Version version, SpatialReference spatialReference, boolean changelogEnabled, DatabaseMetaData metadata) throws SQLException {
        this.version = version;
        this.spatialReference = spatialReference;
        this.changelogEnabled = changelogEnabled;
        this.vendorProductName = metadata.getDatabaseProductName();
        this.vendorProductVersion = metadata.getDatabaseProductVersion();
        this.vendorMajorVersion = metadata.getDatabaseMajorVersion();
        this.vendorMinorVersion = metadata.getDatabaseMinorVersion();
    }

    public static DatabaseMetadata of(
            Version version, SpatialReference spatialReference, boolean changelogEnabled, DatabaseMetaData metadata) throws SQLException {
        return new DatabaseMetadata(version, spatialReference, changelogEnabled, metadata);
    }

    public Version getVersion() {
        return version;
    }

    public SpatialReference getSpatialReference() {
        return spatialReference;
    }

    public boolean isChangelogEnabled() {
        return changelogEnabled;
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
