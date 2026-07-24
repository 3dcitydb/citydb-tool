/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.common.Child;
import org.citydb.model.common.InlineProperty;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.util.CopySession;

import java.util.Objects;
import java.util.Optional;

public class GeometryProperty extends Property<GeometryProperty> implements InlineProperty<Geometry<?>> {
    private Geometry<?> geometry;
    private String lod;

    private GeometryProperty(Name name) {
        super(name);
    }

    private GeometryProperty(Name name, Geometry<?> geometry) {
        super(name, DataType.GEOMETRY_PROPERTY);
        Objects.requireNonNull(geometry, "The geometry must not be null.");
        this.geometry = asChild(geometry);
    }

    public static GeometryProperty of(Name name, Geometry<?> geometry) {
        return new GeometryProperty(name, geometry);
    }

    @Override
    public Geometry<?> getObject() {
        return geometry;
    }

    @Override
    public GeometryProperty setObject(Geometry<?> geometry) {
        if (geometry != null) {
            this.geometry = asChild(geometry);
        }

        return this;
    }

    public Optional<String> getLod() {
        return Optional.ofNullable(lod);
    }

    public GeometryProperty setLod(String lod) {
        this.lod = lod;
        return this;
    }

    public GeometryProperty setLod(int lod) {
        this.lod = String.valueOf(lod);
        return this;
    }

    @Override
    public boolean removeFromParent() {
        Child parent = getParent().orElse(null);
        if (parent instanceof Feature feature) {
            return feature.getGeometries().remove(this);
        } else if (parent instanceof Attribute attribute) {
            return attribute.getProperties().remove(this);
        } else {
            return false;
        }
    }

    @Override
    protected GeometryProperty createClone(CopySession session) {
        return new GeometryProperty(getName());
    }

    @Override
    protected void copyPropertiesTo(Child clone, CopySession session) {
        GeometryProperty property = (GeometryProperty) clone;
        super.copyPropertiesTo(property, session);

        property.geometry = property.asChild(copy(geometry, session));
        property.lod = lod;
    }

    @Override
    GeometryProperty self() {
        return this;
    }
}
