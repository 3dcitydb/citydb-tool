/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.feature;

import org.citydb.model.feature.Feature;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.operation.exporter.hierarchy.HierarchyBuilder;

import java.sql.ResultSet;
import java.sql.SQLException;

public class FeatureHierarchyExporter extends DatabaseExporter {

    public FeatureHierarchyExporter(ExportHelper helper) throws SQLException {
        super(helper);
        stmt = helper.getConnection().prepareStatement(adapter.getSchemaAdapter().getFeatureHierarchyQuery());
    }

    public Feature doExport(long id) throws ExportException, SQLException {
        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            return HierarchyBuilder.newInstance(id, helper)
                    .initialize(rs)
                    .build()
                    .getFeature(id);
        }
    }
}
