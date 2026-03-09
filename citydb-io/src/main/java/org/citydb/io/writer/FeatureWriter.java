/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.writer;

import org.citydb.model.feature.Feature;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public interface FeatureWriter extends AutoCloseable {
    CompletableFuture<Boolean> write(Feature feature) throws WriteException;

    default void write(Feature feature, BiConsumer<Boolean, Throwable> onCompletion) throws WriteException {
        write(feature).whenComplete(onCompletion);
    }

    void cancel();

    @Override
    void close() throws WriteException;
}
