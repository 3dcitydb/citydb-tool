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

import org.citydb.database.schema.ValidityTime;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.literal.TimestampLiteral;
import org.citydb.query.filter.operation.BooleanExpression;
import org.citydb.query.filter.operation.Operators;

import java.time.OffsetDateTime;

public class QueryHelper {

    public static Query getAllTopLevelFeatures() {
        return new Query();
    }

    public static Query getValidTopLevelFeatures(ValidityTime time) {
        return new Query().setFilter(Filter.of(isInvalid(time)));
    }

    public static BooleanExpression isValid(ValidityTime time) {
        return PropertyRef.of(time.to()).isNull();
    }

    public static BooleanExpression wasValidAt(OffsetDateTime timestamp, ValidityTime time) {
        return wasValidAt(timestamp, time, false);
    }

    public static BooleanExpression wasValidAt(OffsetDateTime timestamp, ValidityTime time, boolean lenient) {
        return wasValidBetween(timestamp, timestamp, time, lenient);
    }

    public static BooleanExpression wasValidBetween(OffsetDateTime lowerBound, OffsetDateTime upperBound, ValidityTime time) {
        return wasValidBetween(lowerBound, upperBound, time, false);
    }

    public static BooleanExpression wasValidBetween(OffsetDateTime lowerBound, OffsetDateTime upperBound, ValidityTime time, boolean lenient) {
        PropertyRef from = PropertyRef.of(time.from());
        PropertyRef to = PropertyRef.of(time.to());
        return Operators.and(lenient ?
                        from.isNull().or(from.le(TimestampLiteral.of(upperBound))) :
                        from.le(TimestampLiteral.of(upperBound)),
                isValid(time).or(to.gt(TimestampLiteral.of(lowerBound))));
    }

    public static BooleanExpression isInvalid(ValidityTime time) {
        return PropertyRef.of(time.to()).isNotNull();
    }

    public static BooleanExpression wasInvalidAt(OffsetDateTime timestamp, ValidityTime time) {
        return PropertyRef.of(time.to()).le(TimestampLiteral.of(timestamp));
    }
}
