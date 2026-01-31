/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
