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

import org.citydb.database.schema.ValidityReference;
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

    public static Query getValidTopLevelFeatures(ValidityReference reference) {
        return new Query().setFilter(Filter.of(isValid(reference)));
    }

    public static BooleanExpression isValid(ValidityReference reference) {
        return PropertyRef.of(reference.to()).isNull();
    }

    public static BooleanExpression wasValidAt(OffsetDateTime timestamp, ValidityReference reference) {
        return wasValidAt(timestamp, reference, false);
    }

    public static BooleanExpression wasValidAt(OffsetDateTime timestamp, ValidityReference reference, boolean lenient) {
        return wasValidBetween(timestamp, timestamp, reference, lenient);
    }

    public static BooleanExpression wasValidBetween(OffsetDateTime lowerBound, OffsetDateTime upperBound, ValidityReference reference) {
        return wasValidBetween(lowerBound, upperBound, reference, false);
    }

    public static BooleanExpression wasValidBetween(OffsetDateTime lowerBound, OffsetDateTime upperBound, ValidityReference reference, boolean lenient) {
        PropertyRef from = PropertyRef.of(reference.from());
        PropertyRef to = PropertyRef.of(reference.to());
        return Operators.and(lenient ?
                        from.isNull().or(from.le(TimestampLiteral.of(upperBound))) :
                        from.le(TimestampLiteral.of(upperBound)),
                isValid(reference).or(to.gt(TimestampLiteral.of(lowerBound))));
    }

    public static BooleanExpression isInvalid(ValidityReference reference) {
        return PropertyRef.of(reference.to()).isNotNull();
    }

    public static BooleanExpression wasInvalidAt(OffsetDateTime timestamp, ValidityReference reference) {
        return PropertyRef.of(reference.to()).le(TimestampLiteral.of(timestamp));
    }
}
