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

package org.citydb.database.adapter;

import org.citydb.database.schema.*;
import org.citydb.database.srs.SpatialReferenceType;
import org.citydb.database.util.IndexHelper;
import org.citydb.database.util.OperationHelper;
import org.citydb.database.util.SqlHelper;
import org.citydb.sqlbuilder.common.SqlObject;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.util.Optional;
import java.util.Set;

public abstract class SchemaAdapter {
    protected final DatabaseAdapter adapter;
    private final SqlHelper sqlHelper;
    private final IndexHelper indexHelper;
    private SchemaMapping schemaMapping;

    protected SchemaAdapter(DatabaseAdapter adapter) {
        this.adapter = adapter;
        sqlHelper = SqlHelper.newInstance(adapter);
        indexHelper = IndexHelper.newInstance(adapter);
    }

    public abstract String getDefaultSchema();

    public abstract Optional<Table> getDummyTable();

    public abstract String getNextSequenceValues(Sequence sequence);

    public abstract int getMaximumBatchSize();

    public abstract int getMaximumNumberOfItemsForInOperator();

    public abstract String getFeatureHierarchyQuery();

    public abstract SqlObject getRecursiveImplicitGeometryQuery(Select featureQuery);

    public abstract Select getRecursiveLodQuery(Set<String> lods, boolean requireAll, int searchDepth, Table table);

    public abstract String getCreateIndex(Index index, boolean ignoreNulls);

    public abstract String getDropIndex(Index index);

    public abstract String getIndexExists(Index index);

    public abstract OperationHelper getOperationHelper();

    protected abstract String getCityDBVersion();

    protected abstract String getDatabaseSrs();

    protected abstract String getSpatialReference(int srid);

    protected abstract SpatialReferenceType getSpatialReferenceType(String type);

    void buildSchemaMapping() throws SchemaException {
        schemaMapping = SchemaMappingBuilder.newInstance().build(adapter);
    }

    public SchemaMapping getSchemaMapping() {
        return schemaMapping;
    }

    public SqlHelper getSqlHelper() {
        return sqlHelper;
    }

    public IndexHelper getIndexHelper() {
        return indexHelper;
    }

    public String getCreateIndex(Index index) {
        return getCreateIndex(index, false);
    }
}
