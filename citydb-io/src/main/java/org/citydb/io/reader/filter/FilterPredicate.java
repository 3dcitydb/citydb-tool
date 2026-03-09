/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader.filter;

import org.citydb.model.feature.Feature;

@FunctionalInterface
public interface FilterPredicate {
    boolean test(Feature feature) throws FilterException;
}
