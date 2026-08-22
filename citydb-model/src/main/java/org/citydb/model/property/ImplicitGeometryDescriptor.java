/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.common.ExternalFile;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ImplicitGeometryDescriptor implements Serializable {
    private String geometryReference;
    private ExternalFile libraryObject;
    private Set<String> appearanceReferences;

    private ImplicitGeometryDescriptor(String geometryReference) {
        this.geometryReference = geometryReference;
    }

    private ImplicitGeometryDescriptor(ExternalFile libraryObject) {
        this.libraryObject = libraryObject != null ? libraryObject.copy() : null;
    }

    public static ImplicitGeometryDescriptor of(String geometryReference) {
        return new ImplicitGeometryDescriptor(geometryReference);
    }

    public static ImplicitGeometryDescriptor of(ExternalFile libraryObject) {
        return new ImplicitGeometryDescriptor(libraryObject);
    }

    public static ImplicitGeometryDescriptor of(ImplicitGeometry implicitGeometry) {
        Objects.requireNonNull(implicitGeometry, "The implicit geometry must not be null.");
        ImplicitGeometryDescriptor descriptor;

        Geometry<?> geometry = implicitGeometry.getGeometry().orElse(null);
        if (geometry != null) {
            descriptor = new ImplicitGeometryDescriptor(geometry.getOrCreateObjectId());
            implicitGeometry.getAppearances().forEach(property ->
                    descriptor.addAppearanceReference(property.getObject().getOrCreateObjectId()));
        } else {
            descriptor = new ImplicitGeometryDescriptor(implicitGeometry.getLibraryObject()
                    .orElseThrow(() -> new IllegalStateException("The implicit geometry contains neither a " +
                            "geometry nor a library object.")));
        }

        return descriptor;
    }

    public Optional<String> getGeometryReference() {
        return Optional.ofNullable(geometryReference);
    }

    public Optional<ExternalFile> getLibraryObject() {
        return Optional.ofNullable(libraryObject);
    }

    public boolean hasAppearanceReferences() {
        return appearanceReferences != null && !appearanceReferences.isEmpty();
    }

    public Set<String> getAppearanceReferences() {
        if (appearanceReferences == null) {
            appearanceReferences = new HashSet<>();
        }

        return appearanceReferences;
    }

    public ImplicitGeometryDescriptor setAppearanceReferences(Set<String> appearanceReferences) {
        this.appearanceReferences = appearanceReferences;
        return this;
    }

    public ImplicitGeometryDescriptor addAppearanceReference(String appearanceReference) {
        if (appearanceReference != null) {
            getAppearanceReferences().add(appearanceReference);
        }

        return this;
    }

    public ImplicitGeometryDescriptor copy() {
        ImplicitGeometryDescriptor clone = geometryReference != null
                ? new ImplicitGeometryDescriptor(geometryReference)
                : new ImplicitGeometryDescriptor(libraryObject);

        return clone.setAppearanceReferences(appearanceReferences != null
                ? new HashSet<>(appearanceReferences)
                : null);
    }
}
