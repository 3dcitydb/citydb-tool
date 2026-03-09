/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.feature;

import org.citydb.model.common.Name;

@FunctionalInterface
public interface FeatureTypeProvider {
    Name getName();
}
