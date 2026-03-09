/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public static BooleanExpression validAt(OffsetDateTime timestamp, ValidityReference reference) {
        return validAt(timestamp, reference, false);
    }

    public static BooleanExpression validAt(OffsetDateTime timestamp, ValidityReference reference, boolean lenient) {
        PropertyRef from = PropertyRef.of(reference.from());
        PropertyRef to = PropertyRef.of(reference.to());
        return Operators.and(lenient ?
                        from.isNull().or(from.le(TimestampLiteral.of(timestamp))) :
                        from.le(TimestampLiteral.of(timestamp)),
                isValid(reference).or(to.gt(TimestampLiteral.of(timestamp))));
    }

    public static BooleanExpression isInvalid(ValidityReference reference) {
        return PropertyRef.of(reference.to()).isNotNull();
    }

    public static BooleanExpression invalidAt(OffsetDateTime timestamp, ValidityReference reference) {
        return PropertyRef.of(reference.to()).le(TimestampLiteral.of(timestamp));
    }
}
