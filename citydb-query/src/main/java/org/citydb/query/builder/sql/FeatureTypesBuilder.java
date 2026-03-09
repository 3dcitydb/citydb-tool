/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.schema.FeatureType;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.sqlbuilder.literal.IntegerLiteral;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.In;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
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

    void build(Set<FeatureType> featureTypes, Select select, SqlContext context) throws QueryBuildException {
        select.where(build(featureTypes, context.getTable()));
    }

    void build(FeatureType featureType, Select select, SqlContext context) throws QueryBuildException {
        build(Set.of(featureType), select, context);
    }

    BooleanExpression build(Set<FeatureType> featureTypes, Table table) throws QueryBuildException {
        return build(helper.getSchemaMapping().getObjectClassIds(featureTypes), table.column("objectclass_id"));
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
