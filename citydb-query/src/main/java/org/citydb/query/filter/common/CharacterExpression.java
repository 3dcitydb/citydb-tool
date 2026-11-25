/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.literal.StringLiteral;

public sealed interface CharacterExpression extends ScalarExpression permits
        Function, PropertyRef, StringLiteral {
}
