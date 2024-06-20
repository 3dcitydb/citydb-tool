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

import org.citydb.database.schema.FeatureType;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.schema.Table;

import java.util.Set;

public class FeatureTypesBuilder {
    private final BuilderHelper helper;

    private FeatureTypesBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static FeatureTypesBuilder of(BuilderHelper helper) {
        return new FeatureTypesBuilder(helper);
    }

    BooleanExpression build(Set<FeatureType> featureTypes, Table table) throws QueryBuildException {
        return build(helper.getSchemaMapping().getObjectClassIds(featureTypes), table.column("objectclass_id"));
    }

    BooleanExpression build(Set<FeatureType> featureTypes, SqlContext context) throws QueryBuildException {
        return build(featureTypes, context.getTable());
    }

    BooleanExpression build(FeatureType featureType, SqlContext context) throws QueryBuildException {
        return build(Set.of(featureType), context);
    }

    BooleanExpression build(FeatureType featureType, Table table) throws QueryBuildException {
        return build(Set.of(featureType), table);
    }

    private BooleanExpression build(Set<Integer> ids, org.citydb.sqlbuilder.schema.Column column) throws QueryBuildException {
        if (ids.size() == 1) {
            return Operators.eq(column, IntegerLiteral.of(ids.iterator().next()));
        } else if (ids.size() > 1) {
            return In.of(column, ids.stream()
                    .map(IntegerLiteral::of)
                    .toList());
        } else {
            throw new QueryBuildException("Failed to build object class id filter for selected feature types.");
        }
    }
}
