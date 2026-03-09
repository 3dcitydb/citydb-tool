/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.appearance;

import org.citydb.model.appearance.SurfaceData;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;

import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class SurfaceDataExporter extends DatabaseExporter {

    public SurfaceDataExporter(ExportHelper helper) {
        super(helper);
    }

    protected <T extends SurfaceData<?>> T doExport(T surfaceData, ResultSet rs) throws SQLException {
        surfaceData.setObjectId(rs.getString("sd_objectid"))
                .setIdentifier(rs.getString("sd_identifier"))
                .setIdentifierCodeSpace(rs.getString("sd_identifier_codespace"))
                .setIsFront(getBoolean("is_front", rs));

        return surfaceData;
    }
}
