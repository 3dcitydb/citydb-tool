/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.operation.BooleanExpression;

public interface Predicate extends BooleanExpression {
    BooleanExpression negate();
}
