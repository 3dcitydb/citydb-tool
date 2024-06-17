/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
