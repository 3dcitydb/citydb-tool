/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.operation.BinarySpatialPredicate;
import org.citydb.query.filter.operation.DWithin;
import org.citydb.query.filter.operation.Operators;

public interface GeometryExpression extends Argument {

    default BinarySpatialPredicate intersects(GeometryExpression operand) {
        return Operators.intersects(this, operand);
    }

    default BinarySpatialPredicate contains(GeometryExpression operand) {
        return Operators.contains(this, operand);
    }

    default BinarySpatialPredicate crosses(GeometryExpression operand) {
        return Operators.crosses(this, operand);
    }

    default BinarySpatialPredicate disjoint(GeometryExpression operand) {
        return Operators.disjoint(this, operand);
    }

    default BinarySpatialPredicate equals(GeometryExpression operand) {
        return Operators.equals(this, operand);
    }

    default BinarySpatialPredicate overlaps(GeometryExpression operand) {
        return Operators.overlaps(this, operand);
    }

    default BinarySpatialPredicate touches(GeometryExpression operand) {
        return Operators.touches(this, operand);
    }

    default BinarySpatialPredicate within(GeometryExpression operand) {
        return Operators.within(this, operand);
    }

    default DWithin dWithin(GeometryExpression operand, Distance distance) {
        return Operators.dWithin(this, operand, distance);
    }

    default DWithin beyond(GeometryExpression operand, Distance distance) {
        return Operators.beyond(this, operand, distance);
    }
}
