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

package org.citydb.operation.exporter;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.model.address.Address;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Referencable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.operation.exporter.feature.FeatureExporter;
import org.citydb.operation.exporter.geometry.ImplicitGeometryExporter;
import org.citydb.operation.exporter.options.LodOptions;
import org.citydb.operation.exporter.util.LodFilter;
import org.citydb.operation.exporter.util.Postprocessor;
import org.citydb.operation.exporter.util.SurfaceDataMapper;
import org.citydb.operation.exporter.util.TableHelper;
import org.citydb.sqlbuilder.util.PlainText;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

public class ExportHelper {
    private final DatabaseAdapter adapter;
    private final ExportOptions options;
    private final Connection connection;
    private final SchemaMapping schemaMapping;
    private final SpatialReference targetSrs;
    private final LodFilter lodFilter;
    private final Postprocessor postprocessor;
    private final TableHelper tableHelper;
    private final SurfaceDataMapper surfaceDataMapper = new SurfaceDataMapper();
    private final Set<String> featureIdCache = new HashSet<>();
    private final Set<String> surfaceDataIdCache = new HashSet<>();
    private final Set<String> implicitGeometryIdCache = new HashSet<>();
    private final Set<String> addressIdCache = new HashSet<>();
    private final Set<String> externalFileIdCache = new HashSet<>();

    ExportHelper(DatabaseAdapter adapter, ExportOptions options) throws SQLException, SrsException {
        this.adapter = adapter;
        this.options = options;

        connection = adapter.getPool().getConnection();
        schemaMapping = adapter.getSchemaAdapter().getSchemaMapping();
        targetSrs = adapter.getGeometryAdapter().getSpatialReference(options.getTargetSrs().orElse(null));
        lodFilter = new LodFilter(options.getLodOptions().orElseGet(LodOptions::new));
        postprocessor = new Postprocessor(this);
        tableHelper = new TableHelper(this);
    }

    public DatabaseAdapter getAdapter() {
        return adapter;
    }

    public ExportOptions getOptions() {
        return options;
    }

    public SchemaMapping getSchemaMapping() {
        return schemaMapping;
    }

    public Connection getConnection() {
        return connection;
    }

    public LodFilter getLodFilter() {
        return lodFilter;
    }

    public SurfaceDataMapper getSurfaceDataMapper() {
        return surfaceDataMapper;
    }

    public TableHelper getTableHelper() {
        return tableHelper;
    }

    public int getSRID() {
        return targetSrs.getSRID();
    }

    public String getSrsIdentifier() {
        return targetSrs.getIdentifier();
    }

    public String createId() {
        return "ID_" + UUID.randomUUID();
    }

    public String getOrCreateId(Referencable object) {
        if (object.getObjectId().isPresent()) {
            return object.getObjectId().get();
        } else {
            String objectId = createId();
            object.setObjectId(objectId);
            return objectId;
        }
    }

    public boolean lookupAndPut(Feature feature) {
        String objectId = feature.getObjectId().orElse(null);
        return objectId != null && !featureIdCache.add(objectId);
    }

    public boolean lookupAndPut(SurfaceData<?> surfaceData) {
        String objectId = surfaceData.getObjectId().orElse(null);
        return objectId != null && !surfaceDataIdCache.add(objectId);
    }

    public boolean lookupAndPut(ImplicitGeometry geometry) {
        String objectId = geometry.getObjectId().orElse(null);
        return objectId != null && !implicitGeometryIdCache.add(objectId);
    }

    public boolean lookupAndPut(Address address) {
        String objectId = address.getObjectId().orElse(null);
        return objectId != null && !addressIdCache.add(objectId);
    }

    public boolean lookupAndPut(ExternalFile externalFile) {
        String objectId = externalFile.getObjectId().orElse(null);
        return objectId != null && !externalFileIdCache.add(objectId);
    }

    public String getInOperator(String column, Set<Long> values) {
        if (values.isEmpty()) {
            return column + " = 0";
        } else if (values.size() == 1) {
            return column + " = " + values.iterator().next();
        }

        int i = 0;
        StringBuilder builder = new StringBuilder();
        Iterator<Long> iterator = values.iterator();
        while (iterator.hasNext()) {
            if (i == 0) {
                if (!builder.isEmpty()) {
                    builder.append(" or ");
                }

                builder.append(column).append(" in (");
            }

            builder.append(iterator.next());
            if (++i == adapter.getSchemaAdapter().getMaximumNumberOfItemsForInOperator() || !iterator.hasNext()) {
                builder.append(")");
                i = 0;
            } else {
                builder.append(",");
            }
        }

        return builder.toString();
    }

    public String getTransformOperator(String column) {
        return adapter.getDatabaseMetadata().getSpatialReference().getSRID() == getSRID() ?
                column :
                adapter.getGeometryAdapter().getSpatialOperationHelper()
                        .transform(PlainText.of(column), getSRID())
                        .toString();
    }

    Feature exportFeature(long id, long sequenceId) throws ExportException {
        try {
            Feature feature = tableHelper.getOrCreateExporter(FeatureExporter.class).doExport(id);
            feature.getDescriptor().ifPresent(descriptor -> descriptor.setSequenceId(sequenceId));
            postprocessor.process(feature);
            return feature;
        } catch (Exception e) {
            throw new ExportException("Failed to export feature (ID: " + id + ").", e);
        } finally {
            clear();
        }
    }

    ImplicitGeometry exportImplicitGeometry(long id) throws ExportException {
        try {
            ImplicitGeometry implicitGeometry = tableHelper.getOrCreateExporter(ImplicitGeometryExporter.class)
                    .doExport(id);
            postprocessor.process(implicitGeometry);
            return implicitGeometry;
        } catch (Exception e) {
            throw new ExportException("Failed to export implicit geometry (ID: " + id + ").", e);
        } finally {
            clear();
        }
    }

    private void clear() {
        surfaceDataMapper.clear();
        featureIdCache.clear();
        surfaceDataIdCache.clear();
        implicitGeometryIdCache.clear();
        addressIdCache.clear();
        externalFileIdCache.clear();
    }

    protected void close() throws ExportException, SQLException {
        try {
            tableHelper.close();
        } finally {
            connection.close();
        }
    }
}
