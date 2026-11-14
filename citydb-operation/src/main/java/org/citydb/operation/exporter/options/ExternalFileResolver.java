/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.options;

@FunctionalInterface
public interface ExternalFileResolver {
    String resolve(String folder, String fileName);
}
