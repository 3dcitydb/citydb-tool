/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.appearance;

import org.citydb.database.schema.Table;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;

public abstract class SurfaceDataImporter extends DatabaseImporter {

    public SurfaceDataImporter(ImportHelper helper) throws SQLException {
        super(Table.SURFACE_DATA, helper);
    }

    long doImport(SurfaceData<?> surfaceData, long surfaceDataId) throws ImportException, SQLException {
        String objectId = surfaceData.getObjectId().orElse(null);

        stmt.setLong(1, surfaceDataId);
        stmt.setString(2, surfaceData.getOrCreateObjectId());
        setStringOrNull(3, surfaceData.getIdentifier().orElse(null));
        setStringOrNull(4, surfaceData.getIdentifierCodeSpace().orElse(null));
        setIntegerOrNull(5, surfaceData.isFront().map(v -> v ? 1 : 0).orElse(null));
        setIntegerOrNull(6, schemaMapping.getFeatureType(surfaceData.getName()).getId());

        addBatch();
        cacheTarget(CacheType.SURFACE_DATA, objectId, surfaceDataId);
        tableHelper.getOrCreateImporter(SurfaceDataMappingImporter.class).doImport(surfaceData, surfaceDataId);

        return surfaceDataId;
    }

}
