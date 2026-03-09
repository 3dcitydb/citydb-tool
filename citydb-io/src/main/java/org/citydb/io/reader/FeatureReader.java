/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader;

import org.citydb.model.feature.Feature;

import java.util.function.Consumer;

public interface FeatureReader extends AutoCloseable {
    void read(Consumer<Feature> consumer) throws ReadException;

    void cancel();

    @Override
    void close() throws ReadException;
}
