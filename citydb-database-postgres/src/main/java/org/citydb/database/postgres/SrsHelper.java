/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2026
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

package org.citydb.database.postgres;

import org.citydb.core.version.Version;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SpatialReferenceType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Optional;

public class SrsHelper extends org.citydb.database.util.SrsHelper {

    SrsHelper(DatabaseAdapter adapter) {
        super(adapter);
    }

    @Override
    public Optional<SpatialReference> getDatabaseSrs(String schemaName, Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select srid, srs_name, coord_ref_sys_name, " +
                     "coord_ref_sys_kind, wktext " +
                     "from citydb_pkg.db_metadata('" + schemaName + "')")) {
            if (rs.next()) {
                return Optional.of(SpatialReference.of(rs.getInt("srid"),
                        getSpatialReferenceType(rs.getString("coord_ref_sys_kind")),
                        rs.getString("coord_ref_sys_name"),
                        rs.getString("srs_name"),
                        rs.getString("wktext")));
            }
        }

        return Optional.empty();
    }

    @Override
    protected SpatialReference getSpatialReference(int srid, String identifier, Connection connection) throws SQLException {
        String query = adapter.getDatabaseMetadata().getVersion().compareTo(Version.of(5, 1, 0)) < 0 ?
                "select split_part(srtext, '\"', 2) as coord_ref_sys_name, " +
                        "split_part(srtext, '[', 1) as coord_ref_sys_kind, " +
                        "srtext as wktext from spatial_ref_sys where srid = " + srid :
                "select coord_ref_sys_name, coord_ref_sys_kind, wktext " +
                        "from citydb_pkg.get_coord_ref_sys_info(" + srid + ")";

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            if (rs.next()) {
                return SpatialReference.of(srid,
                        getSpatialReferenceType(rs.getString("coord_ref_sys_kind")),
                        rs.getString("coord_ref_sys_name"),
                        identifier,
                        rs.getString("wktext"));
            }
        }

        return null;
    }

    @Override
    protected SpatialReferenceType getSpatialReferenceType(String type) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "PROJCRS", "PROJECTEDCRS", "PROJCS" -> SpatialReferenceType.PROJECTED_CRS;
            case "GEOGCRS", "GEOGRAPHICCRS", "GEOGCS" -> SpatialReferenceType.GEOGRAPHIC_CRS;
            case "GEODCRS", "GEODETICCRS" -> SpatialReferenceType.GEODETIC_CRS;
            case "GEOCCS" -> SpatialReferenceType.GEOCENTRIC_CRS;
            case "COMPOUNDCRS", "COMPDCS", "COMPD_CS" -> SpatialReferenceType.COMPOUND_CRS;
            case "ENGCRS", "ENGINEERINGCRS", "LOCAL_CS" -> SpatialReferenceType.ENGINEERING_CRS;
            default -> SpatialReferenceType.UNKNOWN_CRS;
        };
    }
}
