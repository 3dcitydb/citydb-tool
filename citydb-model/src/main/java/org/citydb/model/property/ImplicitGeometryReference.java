/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.common.ExternalFile;
import org.citydb.model.geometry.ImplicitGeometry;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class ImplicitGeometryReference implements Serializable {
    private final String objectId;
    private final ImplicitGeometryDescriptor descriptor;

    private ImplicitGeometryReference(String objectId, ImplicitGeometryDescriptor descriptor) {
        this.objectId = Objects.requireNonNull(objectId, "The object id must not be null.");
        this.descriptor = descriptor;
    }

    public static ImplicitGeometryReference of(String objectId) {
        return new ImplicitGeometryReference(objectId, null);
    }

    public static ImplicitGeometryReference of(String objectId, ImplicitGeometryDescriptor descriptor) {
        return new ImplicitGeometryReference(objectId, descriptor != null ? descriptor.copy() : null);
    }

    public static ImplicitGeometryReference of(ImplicitGeometry implicitGeometry) {
        Objects.requireNonNull(implicitGeometry, "The implicit geometry must not be null.");
        return new ImplicitGeometryReference(implicitGeometry.getOrCreateObjectId(),
                ImplicitGeometryDescriptor.of(implicitGeometry));
    }

    public static ImplicitGeometryReference of(ExternalFile libraryObject) {
        Objects.requireNonNull(libraryObject, "The library object must not be null.");
        return new ImplicitGeometryReference(libraryObject.getOrCreateObjectId(),
                ImplicitGeometryDescriptor.of(libraryObject));
    }

    public String getObjectId() {
        return objectId;
    }

    public Optional<ImplicitGeometryDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    public ImplicitGeometryReference copy() {
        return new ImplicitGeometryReference(objectId,
                descriptor != null ? descriptor.copy() : null);
    }
}
