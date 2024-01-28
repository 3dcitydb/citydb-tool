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

package org.citydb.operation.importer.appearance;

import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.appearance.*;
import org.citydb.model.common.Reference;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class SurfaceDataPropertyImporter extends DatabaseImporter {

    public SurfaceDataPropertyImporter(ImportHelper helper) throws SQLException {
        super(Table.APPEAR_TO_SURFACE_DATA, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, appearance_id, surface_data_id) values (" +
                String.join(",", Collections.nCopies(3, "?")) + ")";
    }

    public long doImport(SurfaceDataProperty property, long appearanceId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.APPEAR_TO_SURFACE_DATA);

        stmt.setLong(1, propertyId);
        stmt.setLong(2, appearanceId);

        SurfaceData<?> surfaceData = property.getObject().orElse(null);
        if (surfaceData != null) {
            long surfaceDataId = 0;
            if (surfaceData instanceof ParameterizedTexture texture) {
                surfaceDataId = tableHelper.getOrCreateImporter(ParameterizedTextureImporter.class)
                        .doImport(texture);
            } else if (surfaceData instanceof X3DMaterial material) {
                surfaceDataId = tableHelper.getOrCreateImporter(X3DMaterialImporter.class)
                        .doImport(material);
            } else if (surfaceData instanceof GeoreferencedTexture texture) {
                surfaceDataId = tableHelper.getOrCreateImporter(GeoreferencedTextureImporter.class)
                        .doImport(texture);
            }

            if (surfaceDataId > 0) {
                stmt.setLong(3, surfaceDataId);
            } else {
                stmt.setNull(3, Types.BIGINT);
            }
        } else if (property.getReference().isPresent()) {
            Reference reference = property.getReference().get();
            cacheReference(CacheType.SURFACE_DATA, reference, propertyId);
            stmt.setNull(3, Types.BIGINT);
        }

        addBatch();

        return propertyId;
    }

}
