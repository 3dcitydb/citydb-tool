/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.core.function.Pipeline;
import org.citydb.model.common.Child;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModelObjectReader {

    private ModelObjectReader() {
    }

    public static ModelObjectReader newInstance() {
        return new ModelObjectReader();
    }

    public List<Child> read(Path file) throws IOException {
        return read(file, Child.class);
    }

    public <T extends Child> List<T> read(Path file, Class<T> type) throws IOException {
        List<T> result = new ArrayList<>();
        consume(file, type, result::add);
        return result;
    }

    public void consume(Path file, Consumer<Child> consumer) throws IOException {
        consume(file, Child.class, consumer);
    }

    public <T extends Child> void consume(Path file, Class<T> type, Consumer<T> consumer) throws IOException {
        try (ObjectInputStream stream = new ObjectInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            while (true) {
                try {
                    Object object = stream.readObject();
                    if (type.isInstance(object)) {
                        consumer.accept(type.cast(object));
                    }
                } catch (EOFException e) {
                    return;
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to parse model object.", e);
        }
    }

    public <R> R process(Path file, Pipeline<Child, R> pipeline) throws IOException {
        return process(file, Child.class, pipeline);
    }

    public <T extends Child, R> R process(Path file, Class<T> type, Pipeline<T, R> pipeline) throws IOException {
        try (ObjectInputStream stream = new ObjectInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            while (true) {
                try {
                    Object object = stream.readObject();
                    if (type.isInstance(object)) {
                        Pipeline.Action action = pipeline.process(type.cast(object));
                        if (action == Pipeline.Action.STOP) {
                            break;
                        }
                    }
                } catch (EOFException e) {
                    break;
                }
            }

            return pipeline.getResult();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to parse model object.", e);
        }
    }
}
