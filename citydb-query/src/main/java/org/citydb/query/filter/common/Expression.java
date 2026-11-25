/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.literal.Literal;
import org.citydb.query.filter.operation.NumericExpression;

public sealed interface Expression permits Argument, PatternExpression, NumericExpression, Literal {
    void accept(FilterVisitor visitor);
}
