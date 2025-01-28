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

package org.citydb.query;

import org.citydb.model.common.Namespaces;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.literal.TimestampLiteral;
import org.citydb.query.filter.operation.BooleanExpression;
import org.citydb.query.filter.operation.Not;

import java.time.OffsetDateTime;

public class QueryHelper {

    public static Query getAllTopLevelFeatures() {
        return new Query();
    }

    public static Query getActiveTopLevelFeatures() {
        return new Query().setFilter(Filter.of(isActive()));
    }

    public static BooleanExpression isActive() {
        return PropertyRef.of("terminationDate", Namespaces.CORE).isNull();
    }

    public static BooleanExpression wasActiveAt(OffsetDateTime timestamp) {
        return wasActiveBetween(timestamp, timestamp);
    }

    public static BooleanExpression wasActiveBetween(OffsetDateTime lowerBound, OffsetDateTime upperBound) {
        return PropertyRef.of("creationDate", Namespaces.CORE).le(TimestampLiteral.of(upperBound))
                .and(PropertyRef.of("terminationDate", Namespaces.CORE).gt(TimestampLiteral.of(lowerBound))
                        .or(isActive()));
    }

    public static BooleanExpression isTerminated() {
        return Not.of(isActive());
    }

    public static BooleanExpression wasTerminatedAt(OffsetDateTime timestamp) {
        return PropertyRef.of("terminationDate", Namespaces.CORE).le(TimestampLiteral.of(timestamp));
    }
}
