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

import org.citydb.database.metadata.SpatialReferenceType;
import org.citydb.database.schema.*;

import java.util.Iterator;
import java.util.Set;

public abstract class SchemaAdapter {
    protected final DatabaseAdapter adapter;
    private final IndexHelper indexHelper;
    private SchemaMapping schemaMapping;

    protected SchemaAdapter(DatabaseAdapter adapter) {
        this.adapter = adapter;
        indexHelper = IndexHelper.newInstance(adapter);
    }

    public abstract String getDefaultSchema();
    public abstract String getNextSequenceValues(Sequence sequence);
    public abstract int getMaximumBatchSize();
    public abstract int getMaximumNumberOfItemsForInOperator();
    public abstract String getFeatureHierarchyQuery();
    public abstract String getRecursiveImplicitGeometryQuery(String featureQuery);
    public abstract String getCreateIndex(Index index);
    public abstract String getDropIndex(Index index);
    public abstract String getIndexExists(Index index);
    protected abstract String getCityDBVersion();
    protected abstract String getSpatialReference();
    protected abstract SpatialReferenceType getSpatialReferenceType(String type);

    void buildSchemaMapping() throws SchemaException {
        schemaMapping = SchemaMappingBuilder.newInstance().build(adapter);
    }

    public SchemaMapping getSchemaMapping() {
        return schemaMapping;
    }

    public IndexHelper getIndexHelper() {
        return indexHelper;
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
            if (++i == getMaximumNumberOfItemsForInOperator() || !iterator.hasNext()) {
                builder.append(")");
                i = 0;
            } else {
                builder.append(",");
            }
        }

        return builder.toString();
    }
}
