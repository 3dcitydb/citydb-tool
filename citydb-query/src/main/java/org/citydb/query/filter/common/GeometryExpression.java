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

import org.citydb.query.filter.operation.Operators;
import org.citydb.query.filter.operation.SpatialPredicate;

public interface GeometryExpression extends Argument {

    default SpatialPredicate intersects(GeometryExpression operand) {
        return Operators.intersects(this, operand);
    }

    default SpatialPredicate contains(GeometryExpression operand) {
        return Operators.contains(this, operand);
    }

    default SpatialPredicate crosses(GeometryExpression operand) {
        return Operators.crosses(this, operand);
    }

    default SpatialPredicate disjoint(GeometryExpression operand) {
        return Operators.disjoint(this, operand);
    }

    default SpatialPredicate equals(GeometryExpression operand) {
        return Operators.equals(this, operand);
    }

    default SpatialPredicate overlaps(GeometryExpression operand) {
        return Operators.overlaps(this, operand);
    }

    default SpatialPredicate touches(GeometryExpression operand) {
        return Operators.touches(this, operand);
    }

    default SpatialPredicate within(GeometryExpression operand) {
        return Operators.within(this, operand);
    }

    default SpatialPredicate dWithin(GeometryExpression operand) {
        return Operators.dWithin(this, operand);
    }

    default SpatialPredicate beyond(GeometryExpression operand) {
        return Operators.beyond(this, operand);
    }
}
