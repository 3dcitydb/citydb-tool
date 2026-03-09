/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import org.citydb.operation.importer.ImportException;

@FunctionalInterface
public interface ImportLogger {
    void log(ImportLogEntry logEntry) throws ImportException;
}
