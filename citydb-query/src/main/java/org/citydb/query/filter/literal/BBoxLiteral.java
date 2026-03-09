/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.literal;

import org.citydb.model.geometry.Envelope;
import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.GeometryExpression;

public class BBoxLiteral extends Literal<Envelope> implements GeometryExpression {

    private BBoxLiteral(Envelope value) {
        super(value);
    }

    public static BBoxLiteral of(Envelope value) {
        return new BBoxLiteral(value);
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
