/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.model.geometry.Geometry;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.GeometryExpression;

import java.util.List;
import java.util.Objects;

public class GeometryCollectionLiteral extends Literal<List<GeometryLiteral>> implements GeometryExpression {

    private GeometryCollectionLiteral(List<GeometryLiteral> members) {
        super(members);
    }

    public static GeometryCollectionLiteral of(List<? extends Geometry<?>> members) {
        if (Objects.requireNonNull(members, "The members must not be null.").isEmpty()) {
            throw new IllegalArgumentException("A geometry collection literal requires at least one member.");
        }

        return new GeometryCollectionLiteral(members.stream()
                .map(GeometryLiteral::of)
                .toList());
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
