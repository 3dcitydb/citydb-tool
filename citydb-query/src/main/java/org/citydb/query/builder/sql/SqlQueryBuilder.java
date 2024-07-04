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

package org.citydb.query.builder.sql;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.SrsParseException;
import org.citydb.database.metadata.SpatialReference;
import org.citydb.database.schema.FeatureType;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.filter.Filter;
import org.citydb.sqlbuilder.query.Select;

import java.sql.SQLException;
import java.util.Set;

public class SqlQueryBuilder {
    private final BuilderHelper helper;

    private SqlQueryBuilder(DatabaseAdapter adapter) {
        helper = BuilderHelper.of(adapter);
    }

    public static SqlQueryBuilder of(DatabaseAdapter adapter) {
        return new SqlQueryBuilder(adapter);
    }

    public Select build(Query query) throws QueryBuildException {
        Set<FeatureType> featureTypes = helper.getFeatureTypes(query);

        FeatureType featureType = helper.getSchemaMapping().getSuperType(featureTypes);
        SqlContext context = SqlContext.of(featureType, helper);
        Select select = Select.newInstance()
                .select(context.getTable().columns("id", "objectclass_id"))
                .from(context.getTable());

        helper.getFeatureTypesBuilder().build(featureTypes, select, context);

        Filter filter = query.getFilter().orElse(null);
        if (filter != null) {
            try {
                SpatialReference filterSrs = helper.getSpatialReference(query.getFilterSrs().orElse(null));
                helper.getFilterBuilder().build(filter, filterSrs, select, context);
            } catch (SrsParseException | SQLException e) {
                throw new QueryBuildException("The requested filter SRS is not supported.", e);
            }
        }

        if (!select.getJoins().isEmpty()) {
            buildDistinct(select);
        }

        return select;
    }

    private void buildDistinct(Select select) {
        select.distinct(true);
    }
}
