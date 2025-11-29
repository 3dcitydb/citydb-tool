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

import java.io.Serializable;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;

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
    public Referencable setObjectId(String objectId) {
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
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("The MD5 algorithm is not supported.", e);
        }

        StringBuilder objectId = new StringBuilder();
        char[] digits = new char[2];
        for (byte b : md5.digest(getFileLocation().getBytes())) {
            digits[0] = Character.forDigit((b >> 4) & 0xF, 16);
            digits[1] = Character.forDigit((b & 0xF), 16);
            objectId.append(new String(digits));
        }

        setObjectId(objectId.toString());
    }
}
