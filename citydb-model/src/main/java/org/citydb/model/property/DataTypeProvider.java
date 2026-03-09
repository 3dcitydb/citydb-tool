/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.common.Name;

@FunctionalInterface
public interface DataTypeProvider {
    Name getName();
}
