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

package org.citydb.operation.importer.common;

import org.citydb.core.file.FileLocator;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Referencable;
import org.citydb.model.common.Reference;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.reference.CacheType;
import org.citydb.operation.importer.util.TableHelper;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public abstract class DatabaseImporter {
    protected final Table table;
    private final ImportHelper helper;
    protected final DatabaseAdapter adapter;
    protected final SchemaMapping schemaMapping;
    protected final TableHelper tableHelper;
    protected final PreparedStatement stmt;

    private final Integer srid;
    private int batchCounter;

    public DatabaseImporter(Table table, ImportHelper helper) throws SQLException {
        this.table = table;
        this.helper = helper;
        this.adapter = helper.getAdapter();
        this.schemaMapping = helper.getSchemaMapping();
        this.tableHelper = helper.getTableHelper();
        srid = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
        stmt = helper.getConnection().prepareStatement(getInsertStatement());
    }

    protected abstract String getInsertStatement();

    public void logOrThrow(Level level, String message, Throwable cause) throws ImportException {
        helper.logOrThrow(level, message, cause);
    }

    public void logOrThrow(Level level, String message) throws ImportException {
        helper.logOrThrow(level, message, null);
    }

    public String formatMessage(Feature feature, String message) {
        return helper.formatMessage(feature, message);
    }

    public String formatMessage(Referencable object, String message) {
        return helper.formatMessage(object, message);
    }

    public String getObjectSignature(Feature feature) {
        return helper.getObjectSignature(feature);
    }

    public String getObjectSignature(Referencable object) {
        return helper.getObjectSignature(object);
    }

    protected long nextSequenceValue(Sequence sequence) throws SQLException {
        return helper.getSequenceValues().next(sequence);
    }

    protected void cacheTarget(CacheType type, String objectId, long id) {
        if (objectId != null) {
            helper.getOrCreateReferenceCache(type).putTarget(objectId, id);
        }
    }

    protected void cacheReference(CacheType type, Reference reference, long id) {
        if (reference != null) {
            helper.getOrCreateReferenceCache(type).putReference(reference, id);
        }
    }

    protected OffsetDateTime getImportTime() {
        return helper.getImportTime();
    }

    protected FileLocator getFileLocator(ExternalFile file) {
        return helper.getFileLocator(file);
    }

    protected byte[] getBytes(FileLocator locator) throws IOException {
        try (InputStream stream = locator.openStream()) {
            byte[] bytes = stream.readAllBytes();
            if (bytes.length == 0) {
                throw new IOException("The file " + locator.getFileLocation() + " has zero bytes.");
            }

            return bytes;
        }
    }

    protected Object getGeometry(Geometry<?> geometry, boolean force3D) throws ImportException {
        return getGeometry(geometry, srid, force3D);
    }

    protected Object getGeometry(Geometry<?> geometry) throws ImportException {
        return getGeometry(geometry, srid, true);
    }

    protected Object getImplicitGeometry(Geometry<?> geometry) throws ImportException {
        return getGeometry(geometry, null, true);
    }

    protected Object getEnvelope(Envelope envelope) throws ImportException {
        return envelope != null ? getGeometry(envelope.convertToPolygon(), srid, true) : null;
    }

    private Object getGeometry(Geometry<?> geometry, Integer srid, boolean force3D) throws ImportException {
        try {
            return geometry != null ? adapter.getGeometryAdapter().getGeometry(
                    geometry.setSRID(srid).setSrsIdentifier(null), force3D) :
                    null;
        } catch (GeometryException e) {
            throw new ImportException("Failed to convert geometry to database representation.", e);
        }
    }

    protected void addBatch() throws SQLException {
        stmt.addBatch();
        if (++batchCounter == adapter.getSchemaAdapter().getMaximumBatchSize()) {
            for (Table table : tableHelper.getCommitOrder(table)) {
                for (DatabaseImporter importer : tableHelper.getImporters(table)) {
                    importer.executeBatch();
                }
            }
        }
    }

    public void executeBatch() throws SQLException {
        if (batchCounter > 0) {
            stmt.executeBatch();
            batchCounter = 0;
        }
    }

    public void close() throws SQLException {
        stmt.close();
    }
}
