/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader.filter;

import org.citydb.model.feature.Feature;

@FunctionalInterface
public interface Filter {
    enum Result {
        ACCEPT,
        SKIP,
        STOP
    }

    static Filter acceptAll() {
        return feature -> Result.ACCEPT;
    }

    default boolean needsSequentialProcessing() {
        return false;
    }

    Result test(Feature feature) throws FilterException;
}
