/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.address.AddressExporter;
import org.citydb.operation.exporter.appearance.*;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.operation.exporter.feature.FeatureExporter;
import org.citydb.operation.exporter.feature.FeatureHierarchyExporter;
import org.citydb.operation.exporter.geometry.GeometryExporter;
import org.citydb.operation.exporter.geometry.ImplicitGeometryExporter;
import org.citydb.operation.exporter.property.PropertyExporter;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class TableHelper {
    private final ExportHelper helper;
    private final Map<String, DatabaseExporter> exporters = new HashMap<>();

    public TableHelper(ExportHelper helper) {
        this.helper = helper;
    }

    public Table getTable(org.citydb.database.schema.Table table) {
        return Table.of(table.getName(), helper.getAdapter().getConnectionDetails().getSchema());
    }

    public <T extends DatabaseExporter> T getOrCreateExporter(Class<T> type) throws ExportException {
        DatabaseExporter exporter = exporters.get(type.getName());
        if (exporter == null) {
            try {
                if (type == FeatureHierarchyExporter.class) {
                    exporter = new FeatureHierarchyExporter(helper);
                } else if (type == FeatureExporter.class) {
                    exporter = new FeatureExporter(helper);
                } else if (type == GeometryExporter.class) {
                    exporter = new GeometryExporter(helper);
                } else if (type == ImplicitGeometryExporter.class) {
                    exporter = new ImplicitGeometryExporter(helper);
                } else if (type == AppearanceExporter.class) {
                    exporter = new AppearanceExporter(helper);
                } else if (type == AddressExporter.class) {
                    exporter = new AddressExporter(helper);
                } else if (type == PropertyExporter.class) {
                    exporter = new PropertyExporter(helper);
                } else if (type == X3DMaterialExporter.class) {
                    exporter = new X3DMaterialExporter(helper);
                } else if (type == ParameterizedTextureExporter.class) {
                    exporter = new ParameterizedTextureExporter(helper);
                } else if (type == GeoreferencedTextureExporter.class) {
                    exporter = new GeoreferencedTextureExporter(helper);
                } else if (type == SurfaceDataMappingExporter.class) {
                    exporter = new SurfaceDataMappingExporter(helper);
                }

                if (exporter != null) {
                    exporters.put(type.getName(), exporter);
                }
            } catch (SQLException e) {
                throw new ExportException("Failed to build exporter of type " + type.getName() + ".", e);
            }
        }

        if (type.isInstance(exporter)) {
            return type.cast(exporter);
        } else {
            throw new ExportException("Failed to build exporter of type " + type.getName() + ".");
        }
    }

    public void close() throws ExportException, SQLException {
        for (DatabaseExporter exporter : exporters.values()) {
            exporter.close();
        }
    }
}
