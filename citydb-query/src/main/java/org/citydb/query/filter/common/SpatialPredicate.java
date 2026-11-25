/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.query.filter.operation.BinarySpatialPredicate;
import org.citydb.query.filter.operation.DWithin;

public sealed interface SpatialPredicate extends Predicate permits BinarySpatialPredicate, DWithin {
}
