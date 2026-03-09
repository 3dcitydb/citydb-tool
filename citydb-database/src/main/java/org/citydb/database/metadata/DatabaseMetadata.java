/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.metadata;

import org.citydb.core.concurrent.LazyInitializer;
import org.citydb.core.version.Version;
import org.citydb.database.srs.SpatialReference;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DatabaseMetadata {
    private final Version version;
    private final SpatialReference spatialReference;
    private final boolean changelogEnabled;
    private final String vendorProductName;
    private final String vendorProductVersion;
    private final int vendorMajorVersion;
    private final int vendorMinorVersion;
    private final LazyInitializer<String> vendorProductString;
    private final Map<String, DatabaseProperty> properties;

    private DatabaseMetadata(
            Version version, SpatialReference spatialReference, boolean changelogEnabled, DatabaseMetaData metadata,
            Function<DatabaseMetadata, String> vendorProductString, List<DatabaseProperty> properties) throws SQLException {
        this.version = version;
        this.spatialReference = spatialReference;
        this.changelogEnabled = changelogEnabled;
        this.vendorProductName = metadata.getDatabaseProductName();
        this.vendorProductVersion = metadata.getDatabaseProductVersion();
        this.vendorMajorVersion = metadata.getDatabaseMajorVersion();
        this.vendorMinorVersion = metadata.getDatabaseMinorVersion();
        this.vendorProductString = LazyInitializer.of(() -> vendorProductString.apply(this));
        this.properties = Collections.unmodifiableMap(properties.stream().collect(Collectors.toMap(
                property -> property.getId().toLowerCase(Locale.ROOT),
                property -> property,
                (v1, v2) -> v1,
                LinkedHashMap::new)));
    }

    public static DatabaseMetadata of(
            Version version, SpatialReference spatialReference, boolean changelogEnabled, DatabaseMetaData metadata,
            Function<DatabaseMetadata, String> vendorProductString, List<DatabaseProperty> properties) throws SQLException {
        return new DatabaseMetadata(version, spatialReference, changelogEnabled, metadata, vendorProductString,
                properties);
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

    public String getVendorProductString() {
        return vendorProductString.get();
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

    public boolean hasProperties() {
        return !properties.isEmpty();
    }

    public Map<String, DatabaseProperty> getProperties() {
        return properties;
    }

    public Optional<DatabaseProperty> getProperty(String id) {
        return id != null ?
                Optional.ofNullable(properties.get(id.toLowerCase(Locale.ROOT))) :
                Optional.empty();
    }
}
