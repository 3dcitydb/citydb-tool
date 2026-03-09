/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.model.geometry.Geometry;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.GeometryExpression;

public class GeometryLiteral extends Literal<Geometry<?>> implements GeometryExpression {

    private GeometryLiteral(Geometry<?> value) {
        super(value);
    }

    public static GeometryLiteral of(Geometry<?> value) {
        return new GeometryLiteral(value);
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
