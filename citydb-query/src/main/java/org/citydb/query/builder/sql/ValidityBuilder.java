/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.schema.*;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Validity;
import org.citydb.model.feature.ValidityMode;
import org.citydb.model.feature.ValidityReference;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;

public class ValidityBuilder {
    private final BuilderHelper helper;

    private ValidityBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static ValidityBuilder of(BuilderHelper helper) {
        return new ValidityBuilder(helper);
    }

    Optional<BooleanExpression> buildForTopLevelFeature(FeatureType featureType, Table table) throws QueryBuildException {
        Validity validity = helper.getValidity();
        return validity != null
                ? build(validity, featureType, table)
                : Optional.empty();
    }

    Optional<BooleanExpression> buildForJoinedFeature(FeatureType featureType, Table table) throws QueryBuildException {
        Validity validity = helper.getValidity();
        return validity != null && validity.getMode() == ValidityMode.VALID
                ? build(validity, featureType, table)
                : Optional.empty();
    }

    private Optional<BooleanExpression> build(Validity validity, FeatureType featureType, Table table) throws QueryBuildException {
        ValidityReference reference = validity.getReference();
        OffsetDateTime at = validity.getAt().orElse(null);
        return switch (validity.getMode()) {
            case VALID -> Optional.of(at != null
                    ? validAt(featureType, table, reference, at, validity.isLenient())
                    : Operators.isNull(table.column(getColumn(featureType, reference.to()))));
            case INVALID -> Optional.of(at != null
                    ? Operators.le(table.column(getColumn(featureType, reference.to())), timestamp(at))
                    : Operators.isNull(table.column(getColumn(featureType, reference.to())), true));
            case ALL -> Optional.empty();
        };
    }

    private BooleanExpression validAt(FeatureType featureType, Table table, ValidityReference reference,
                                      OffsetDateTime at, boolean lenient) throws QueryBuildException {
        String fromColumn = getColumn(featureType, reference.from());
        String toColumn = getColumn(featureType, reference.to());
        Placeholder timestamp = timestamp(at);

        BooleanExpression from = Operators.le(table.column(fromColumn), timestamp);
        if (lenient) {
            from = Operators.or(Operators.isNull(table.column(fromColumn)), from);
        }

        return Operators.and(from, Operators.or(
                Operators.isNull(table.column(toColumn)),
                Operators.gt(table.column(toColumn), timestamp)));
    }

    private Placeholder timestamp(OffsetDateTime at) {
        return Placeholder.of(Timestamp.from(at.toInstant()));
    }

    private String getColumn(FeatureType featureType, Name name) throws QueryBuildException {
        return Optional.ofNullable(featureType.getProperties().get(name))
                .flatMap(Property::getValue)
                .flatMap(Value::getColumn)
                .map(Column::getName)
                .orElseThrow(() -> new QueryBuildException("Failed to resolve the validity property " + name +
                        " of the feature type " + featureType.getName() + "."));
    }
}
