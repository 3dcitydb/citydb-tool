/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.sql;

import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.Property;
import org.citydb.database.schema.Value;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Validity;
import org.citydb.model.feature.ValidityReference;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.schema.Column;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Optional;

public class ValidityBuilder {
    private static final Name LIFESPAN_TYPE = Name.of("AbstractFeatureWithLifespan", Namespaces.CORE);
    private final BuilderHelper helper;

    private ValidityBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static ValidityBuilder of(BuilderHelper helper) {
        return new ValidityBuilder(helper);
    }

    Optional<BooleanExpression> buildForTopLevelFeature() throws QueryBuildException {
        Validity validity = helper.getValidity();
        return validity != null
                ? build(validity, getTopLevelTable())
                : Optional.empty();
    }

    Optional<BooleanExpression> buildForJoinedFeature(Table table) throws QueryBuildException {
        Validity validity = helper.getValidity();
        if (validity == null) {
            return Optional.empty();
        }

        return switch (validity.getMode()) {
            case VALID -> build(validity, table);
            case INVALID -> Optional.of(validAtTermination(table, validity.getReference()));
            case ALL -> Optional.empty();
        };
    }

    private BooleanExpression validAtTermination(Table table, ValidityReference reference) throws QueryBuildException {
        String toColumn = getColumn(reference.to());
        Column to = table.column(toColumn);
        return Operators.or(
                Operators.isNull(to),
                Operators.ge(to, getTopLevelTable().column(toColumn)));
    }

    private Table getTopLevelTable() {
        return helper.getTopLevelContext().getTable();
    }

    private Optional<BooleanExpression> build(Validity validity, Table table) throws QueryBuildException {
        ValidityReference reference = validity.getReference();
        OffsetDateTime at = validity.getAt().orElse(null);
        return switch (validity.getMode()) {
            case VALID -> Optional.of(at != null
                    ? validAt(table, reference, at, validity.isLenient())
                    : Operators.isNull(table.column(getColumn(reference.to()))));
            case INVALID -> Optional.of(at != null
                    ? Operators.le(table.column(getColumn(reference.to())), timestamp(at))
                    : Operators.isNull(table.column(getColumn(reference.to())), true));
            case ALL -> Optional.empty();
        };
    }

    private BooleanExpression validAt(Table table, ValidityReference reference, OffsetDateTime at,
                                      boolean lenient) throws QueryBuildException {
        String fromColumn = getColumn(reference.from());
        String toColumn = getColumn(reference.to());
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

    private String getColumn(Name name) throws QueryBuildException {
        FeatureType featureType = helper.getSchemaMapping().getFeatureType(LIFESPAN_TYPE);
        return Optional.ofNullable(featureType.getProperties().get(name))
                .flatMap(Property::getValue)
                .flatMap(Value::getColumn)
                .map(org.citydb.database.schema.Column::getName)
                .orElseThrow(() -> new QueryBuildException("Failed to resolve the validity property " + name +
                        " of the feature type " + LIFESPAN_TYPE + "."));
    }
}
