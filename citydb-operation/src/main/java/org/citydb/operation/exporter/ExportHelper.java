/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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
import org.citydb.model.address.Address;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Referencable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.operation.exporter.feature.FeatureExporter;
import org.citydb.operation.exporter.geometry.ImplicitGeometryExporter;
import org.citydb.operation.exporter.util.Postprocessor;
import org.citydb.operation.exporter.util.SurfaceDataMapper;
import org.citydb.operation.exporter.util.TableHelper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ExportHelper {
    private final DatabaseAdapter adapter;
    private final ExportOptions options;
    private final Connection connection;
    private final Postprocessor postprocessor;
    private final SchemaMapping schemaMapping;
    private final TableHelper tableHelper;
    private final int srid;
    private final String srsName;
    private final Set<String> featureIdCache = new HashSet<>();
    private final Set<String> surfaceDataIdCache = new HashSet<>();
    private final Set<String> implicitGeometryIdCache = new HashSet<>();
    private final Set<String> addressIdCache = new HashSet<>();
    private final Set<String> externalFileIdCache = new HashSet<>();

    ExportHelper(DatabaseAdapter adapter, ExportOptions options) throws SQLException {
        this.adapter = adapter;
        this.options = options;

        connection = adapter.getPool().getConnection();
        postprocessor = new Postprocessor();
        schemaMapping = adapter.getSchemaAdapter().getSchemaMapping();
        tableHelper = new TableHelper(this);
        srid = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
        srsName = adapter.getDatabaseMetadata().getSpatialReference().getURI();
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

    public SurfaceDataMapper getSurfaceDataMapper() {
        return postprocessor.getSurfaceDataMapper();
    }

    public TableHelper getTableHelper() {
        return tableHelper;
    }

    public int getSRID() {
        return srid;
    }

    public String getSrsName() {
        return srsName;
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

    Feature exportFeature(long id) throws ExportException {
        try {
            Feature feature = tableHelper.getOrCreateExporter(FeatureExporter.class).doExport(id);
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
        featureIdCache.clear();
        surfaceDataIdCache.clear();
        implicitGeometryIdCache.clear();
        addressIdCache.clear();
        externalFileIdCache.clear();
    }

    protected void close() throws ExportException, SQLException {
        tableHelper.close();
        connection.close();
    }
}
