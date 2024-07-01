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

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.sorting.SortBy;
import org.citydb.query.sorting.SortOrder;
import org.citydb.query.sorting.Sorting;
import org.citydb.sqlbuilder.query.OrderBy;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.Selection;

public class SortingBuilder {
    private final BuilderHelper helper;

    private SortingBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static SortingBuilder of(BuilderHelper helper) {
        return new SortingBuilder(helper);
    }

    void build(Sorting sorting, Select select, SqlContext context) throws QueryBuildException {
        if (sorting.hasSortBy()) {
            helper.getContextBuilder().useLeftJoins(true);
            for (SortBy sortBy : sorting.getSortBy()) {
                select.orderBy(build(sortBy, select, context));
            }
        }
    }

    private OrderBy build(SortBy sortBy, Select select, SqlContext context) throws QueryBuildException {
        PropertyRef propertyRef = sortBy.getProperty()
                .orElseThrow(() -> new QueryBuildException("The sortBy operation lacks a property reference."));
        if (propertyRef.downStream().anyMatch(step -> step.getFilter().isPresent())) {
            throw new QueryBuildException("Step predicates are not allowed for the property reference of " +
                    "a sortBy operation.");
        }

        BuildResult sortExpression = helper.getFilterBuilder().build(propertyRef, select, context);
        sortExpression.requireType(Type.SCALAR_TYPES, () -> "A value of type '" + sortExpression.getType() +
                "' cannot be used as sort expression of a sortBy operation.");

        if (sortExpression.getExpression() instanceof Selection<?> selection) {
            return OrderBy.of(selection, sortBy.getSortOrder() == SortOrder.DESC ?
                    OrderBy.DESCENDING :
                    OrderBy.ASCENDING);
        } else {
            throw new QueryBuildException("Failed to build sortBy operation.");
        }
    }
}
