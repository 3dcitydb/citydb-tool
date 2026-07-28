/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.reader;

import org.citydb.model.feature.Feature;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class FeatureReader implements AutoCloseable {
    private enum State {
        INITIAL,
        CONSUMED,
        FAILED,
        CANCELLED,
        CLOSED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.INITIAL);

    protected abstract void doPrepass(Consumer<Feature> consumer) throws ReadException;

    protected abstract void doRead(Consumer<Feature> consumer) throws ReadException;

    protected abstract void doCancel();

    protected abstract void doClose() throws ReadException;

    public final void prepass(Consumer<Feature> consumer) throws ReadException {
        read(consumer, true);
    }

    public final void read(Consumer<Feature> consumer) throws ReadException {
        read(consumer, false);
    }

    private void read(Consumer<Feature> consumer, boolean prepass) throws ReadException {
        switch (state.get()) {
            case CONSUMED -> throw new ReadException("The data has already been consumed.");
            case FAILED -> throw new ReadException("The reader cannot be used after a failed read.");
            case CANCELLED -> throw new ReadException("The reader has been cancelled.");
            case CLOSED -> throw new ReadException("The reader has already been closed.");
        }

        try {
            if (prepass) {
                doPrepass(consumer);
            } else {
                doRead(consumer);
                state.compareAndSet(State.INITIAL, State.CONSUMED);
            }
        } catch (ReadException e) {
            state.set(State.FAILED);
            throw e;
        } catch (Throwable e) {
            state.set(State.FAILED);
            throw new ReadException("Failed to read input file.", e);
        }
    }

    public final void cancel() {
        if (state.compareAndSet(State.INITIAL, State.CANCELLED)) {
            doCancel();
        }
    }

    @Override
    public final void close() throws ReadException {
        if (state.getAndSet(State.CLOSED) != State.CLOSED) {
            doClose();
        }
    }
}
