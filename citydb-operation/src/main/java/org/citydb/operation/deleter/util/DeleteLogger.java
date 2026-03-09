/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.deleter.util;

import org.citydb.operation.deleter.DeleteException;

@FunctionalInterface
public interface DeleteLogger {
    void log(DeleteLogEntry logEntry) throws DeleteException;
}
