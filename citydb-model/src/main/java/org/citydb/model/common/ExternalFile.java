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

package org.citydb.model.common;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class ExternalFile implements Referencable, Serializable {
    private String uri;
    private transient Path path;
    private String objectId;
    private String mimeType;
    private String mimeTypeCodeSpace;

    private ExternalFile(String uri) {
        setUri(uri).createAndSetObjectId();
    }

    private ExternalFile(Path path) {
        setPath(path).createAndSetObjectId();
    }

    public static ExternalFile of(String uri) {
        return new ExternalFile(uri);
    }

    public static ExternalFile of(Path path) {
        return new ExternalFile(path);
    }

    public Optional<String> getURI() {
        return Optional.ofNullable(uri);
    }

    public ExternalFile setUri(String uri) {
        this.uri = Objects.requireNonNull(uri, "The URI must not be null.");
        path = null;
        return this;
    }

    public Optional<Path> getPath() {
        return Optional.ofNullable(path);
    }

    public ExternalFile setPath(Path path) {
        this.path = Objects.requireNonNull(path, "The path must not be null.")
                .toAbsolutePath()
                .normalize();
        uri = null;
        return this;
    }

    @Override
    public Optional<String> getObjectId() {
        return Optional.of(objectId);
    }

    @Override
    public ExternalFile setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    public Optional<String> getMimeType() {
        return Optional.ofNullable(mimeType);
    }

    public ExternalFile setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public Optional<String> getMimeTypeCodeSpace() {
        return Optional.ofNullable(mimeTypeCodeSpace);
    }

    public ExternalFile setMimeTypeCodeSpace(String mimeTypeCodeSpace) {
        this.mimeTypeCodeSpace = mimeTypeCodeSpace;
        return this;
    }

    public String getFileLocation() {
        return path != null ? path.toString() : uri;
    }

    @Override
    public String toString() {
        return getFileLocation();
    }

    private void createAndSetObjectId() {
        setObjectId("ID_" + UUID.nameUUIDFromBytes(getFileLocation().getBytes()));
    }

    @Serial
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        boolean wasPath = stream.readBoolean();
        if (wasPath && uri != null) {
            path = Path.of(uri);
            uri = null;
        }
    }

    @Serial
    private void writeObject(ObjectOutputStream stream) throws IOException {
        boolean wasPath = false;
        if (path != null) {
            uri = path.toString();
            wasPath = path.getFileSystem() == FileSystems.getDefault();
            path = null;
        }

        stream.defaultWriteObject();
        stream.writeBoolean(wasPath);
    }
}
