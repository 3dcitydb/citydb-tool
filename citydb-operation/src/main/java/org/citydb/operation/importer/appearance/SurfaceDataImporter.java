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

package org.citydb.operation.importer.appearance;

import org.citydb.database.schema.Table;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;

public abstract class SurfaceDataImporter extends DatabaseImporter {

    public SurfaceDataImporter(ImportHelper helper) throws SQLException {
        super(Table.SURFACE_DATA, helper);
    }

    long doImport(SurfaceData<?> surfaceData, long surfaceDataId) throws ImportException, SQLException {
        String objectId = surfaceData.getObjectId().orElse(null);

        stmt.setLong(1, surfaceDataId);
        stmt.setString(2, surfaceData.getOrCreateObjectId());
        stmt.setString(3, surfaceData.getIdentifier().orElse(null));
        stmt.setString(4, surfaceData.getIdentifierCodeSpace().orElse(null));

        Integer isFront = surfaceData.isFront().map(v -> v ? 1 : 0).orElse(null);
        if (isFront != null) {
            stmt.setInt(5, isFront);
        } else {
            stmt.setNull(5, Types.INTEGER);
        }

        stmt.setInt(6, schemaMapping.getFeatureType(surfaceData.getName()).getId());

        addBatch();
        cacheTarget(CacheType.SURFACE_DATA, objectId, surfaceDataId);
        tableHelper.getOrCreateImporter(SurfaceDataMappingImporter.class).doImport(surfaceData, surfaceDataId);

        return surfaceDataId;
    }

}
