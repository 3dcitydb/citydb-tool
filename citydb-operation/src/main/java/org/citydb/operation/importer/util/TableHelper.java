/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.operation.importer.util;

import org.citydb.database.schema.CommitOrder;
import org.citydb.database.schema.Table;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.address.AddressImporter;
import org.citydb.operation.importer.appearance.*;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.feature.FeatureImporter;
import org.citydb.operation.importer.geometry.GeometryImporter;
import org.citydb.operation.importer.geometry.ImplicitGeometryImporter;
import org.citydb.operation.importer.property.*;

import java.sql.SQLException;
import java.util.*;

public class TableHelper {
    private final ImportHelper helper;
    private final Map<String, DatabaseImporter> importers = new HashMap<>();
    private final CommitOrder commitOrder = CommitOrder.of(
            Table.ADDRESS,
            Table.APPEARANCE,
            Table.APPEAR_TO_SURFACE_DATA,
            Table.FEATURE,
            Table.GEOMETRY_DATA,
            Table.IMPLICIT_GEOMETRY,
            Table.PROPERTY,
            Table.SURFACE_DATA,
            Table.SURFACE_DATA_MAPPING,
            Table.TEX_IMAGE);

    public TableHelper(ImportHelper helper) {
        this.helper = helper;
    }

    public String getPrefixedTableName(Table table) {
        return helper.getAdapter().getConnectionDetails().getSchema() + "." + table;
    }

    public List<Table> getCommitOrder() {
        return commitOrder.getCommitOrder();
    }

    public List<Table> getCommitOrder(Table table) {
        return commitOrder.getCommitOrder(table);
    }

    public <T extends DatabaseImporter> T getOrCreateImporter(Class<T> type) throws ImportException {
        DatabaseImporter importer = importers.get(type.getName());
        if (importer == null) {
            try {
                if (type == FeatureImporter.class) {
                    importer = new FeatureImporter(helper);
                } else if (type == GeometryImporter.class) {
                    importer = new GeometryImporter(helper);
                } else if (type == ImplicitGeometryImporter.class) {
                    importer = new ImplicitGeometryImporter(helper);
                } else if (type == AppearanceImporter.class) {
                    importer = new AppearanceImporter(helper);
                } else if (type == AddressImporter.class) {
                    importer = new AddressImporter(helper);
                } else if (type == AttributeImporter.class) {
                    importer = new AttributeImporter(helper);
                } else if (type == FeaturePropertyImporter.class) {
                    importer = new FeaturePropertyImporter(helper);
                } else if (type == GeometryPropertyImporter.class) {
                    importer = new GeometryPropertyImporter(helper);
                } else if (type == ImplicitGeometryPropertyImporter.class) {
                    importer = new ImplicitGeometryPropertyImporter(helper);
                } else if (type == AppearancePropertyImporter.class) {
                    importer = new AppearancePropertyImporter(helper);
                } else if (type == AddressPropertyImporter.class) {
                    importer = new AddressPropertyImporter(helper);
                } else if (type == ParameterizedTextureImporter.class) {
                    importer = new ParameterizedTextureImporter(helper);
                } else if (type == X3DMaterialImporter.class) {
                    importer = new X3DMaterialImporter(helper);
                } else if (type == GeoreferencedTextureImporter.class) {
                    importer = new GeoreferencedTextureImporter(helper);
                } else if (type == SurfaceDataMappingImporter.class) {
                    importer = new SurfaceDataMappingImporter(helper);
                } else if (type == SurfaceDataPropertyImporter.class) {
                    importer = new SurfaceDataPropertyImporter(helper);
                } else if (type == TextureImageImporter.class) {
                    importer = new TextureImageImporter(helper);
                }

                if (importer != null) {
                    importers.put(type.getName(), importer);
                }
            } catch (SQLException e) {
                throw new ImportException("Failed to build importer of type " + type.getName() + ".", e);
            }
        }

        if (type.isInstance(importer)) {
            return type.cast(importer);
        } else {
            throw new ImportException("Failed to build importer of type " + type.getName() + ".");
        }
    }

    public List<DatabaseImporter> getImporters(Table table) {
        List<DatabaseImporter> candidates = new ArrayList<>();
        switch (table) {
            case FEATURE:
                candidates.add(importers.get(FeatureImporter.class.getName()));
                break;
            case GEOMETRY_DATA:
                candidates.add(importers.get(GeometryImporter.class.getName()));
                break;
            case IMPLICIT_GEOMETRY:
                candidates.add(importers.get(ImplicitGeometryImporter.class.getName()));
                break;
            case ADDRESS:
                candidates.add(importers.get(AddressImporter.class.getName()));
                break;
            case APPEARANCE:
                candidates.add(importers.get(AppearanceImporter.class.getName()));
                break;
            case PROPERTY:
                candidates.add(importers.get(AttributeImporter.class.getName()));
                candidates.add(importers.get(FeaturePropertyImporter.class.getName()));
                candidates.add(importers.get(GeometryPropertyImporter.class.getName()));
                candidates.add(importers.get(ImplicitGeometryPropertyImporter.class.getName()));
                candidates.add(importers.get(AppearancePropertyImporter.class.getName()));
                candidates.add(importers.get(AddressPropertyImporter.class.getName()));
                break;
            case SURFACE_DATA:
                candidates.add(importers.get(ParameterizedTextureImporter.class.getName()));
                candidates.add(importers.get(X3DMaterialImporter.class.getName()));
                candidates.add(importers.get(GeoreferencedTextureImporter.class.getName()));
                break;
            case SURFACE_DATA_MAPPING:
                candidates.add(importers.get(SurfaceDataMappingImporter.class.getName()));
                break;
            case APPEAR_TO_SURFACE_DATA:
                candidates.add(importers.get(SurfaceDataPropertyImporter.class.getName()));
                break;
            case TEX_IMAGE:
                candidates.add(importers.get(TextureImageImporter.class.getName()));
                break;
        }

        candidates.removeIf(Objects::isNull);
        return candidates;
    }

    public void close() throws SQLException {
        for (DatabaseImporter importer : importers.values()) {
            importer.close();
        }
    }
}
