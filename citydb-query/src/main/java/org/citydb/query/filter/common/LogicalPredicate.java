/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.operation.BinaryBooleanPredicate;
import org.citydb.query.filter.operation.Not;

public sealed interface LogicalPredicate extends Predicate permits BinaryBooleanPredicate, Not {
}
