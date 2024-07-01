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

package org.citydb.database.util;

import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.operation.BooleanExpression;

public interface SpatialOperationHelper {
    ScalarExpression transform(ScalarExpression operand, int srid);

    BooleanExpression bbox(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression contains(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression crosses(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression disjoint(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression equals(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression intersects(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression overlaps(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression touches(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression within(ScalarExpression leftOperand, ScalarExpression rightOperand);

    BooleanExpression dWithin(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance);

    BooleanExpression beyond(ScalarExpression leftOperand, ScalarExpression rightOperand, ScalarExpression distance);
}
