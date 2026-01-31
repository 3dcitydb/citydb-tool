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

package org.citydb.operation.exporter.common;

import org.citydb.core.file.OutputFile;
import org.citydb.database.adapter.SchemaAdapter;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BlobExporter {
    private final OutputFile outputFile;
    private final SchemaAdapter schemaAdapter;
    private final int batchSize;
    private final PreparedStatement stmt;
    private final Map<Long, ExternalFile> batches = new HashMap<>();

    public BlobExporter(Table table, String idColumn, String blobColumn, ExportHelper helper) throws SQLException {
        outputFile = helper.getOptions().getOutputFile();
        schemaAdapter = helper.getAdapter().getSchemaAdapter();
        batchSize = Math.min(100, schemaAdapter.getMaximumNumberOfItemsForInOperator());
        stmt = helper.getConnection().prepareStatement(getQuery(table, idColumn, blobColumn).toSql());
    }

    private Select getQuery(Table table, String idColumn, String blobColumn) {
        return Select.newInstance()
                .select(table.columns(idColumn, blobColumn))
                .from(table)
                .where(schemaAdapter.getOperationHelper().inArray(table.column(idColumn), Placeholder.empty()));
    }

    public void addBatch(long id, ExternalFile externalFile) throws ExportException, SQLException {
        if (externalFile != null) {
            batches.put(id, externalFile);
            if (batches.size() == batchSize) {
                executeBatch();
            }
        }
    }

    private void executeBatch() throws ExportException, SQLException {
        if (!batches.isEmpty()) {
            try {
                schemaAdapter.getSqlHelper().setLongArrayOrNull(stmt, 1, batches.keySet());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ExternalFile externalFile = batches.get(rs.getLong(1));
                        byte[] buffer = rs.getBytes(2);
                        if (buffer != null && buffer.length > 0) {
                            try (OutputStream out = openStream(externalFile)) {
                                out.write(buffer);
                            } catch (Exception e) {
                                throw new ExportException("Failed to export file '" +
                                        externalFile.getFileLocation() + "'.", e);
                            }
                        }
                    }
                }
            } finally {
                batches.clear();
            }
        }
    }

    private OutputStream openStream(ExternalFile target) throws IOException {
        Optional<Path> path = target.getPath();
        return path.isPresent() ?
                Files.newOutputStream(path.get()) :
                outputFile.newOutputStream(outputFile.resolve(target.getFileLocation()));
    }

    public void close() throws ExportException, SQLException {
        executeBatch();
        stmt.close();
    }
}
