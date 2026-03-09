/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.hierarchy;

import org.citydb.model.address.Address;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.Property;

import java.util.HashMap;
import java.util.Map;

public class Hierarchy {
    private final Map<Long, Feature> features = new HashMap<>();
    private final Map<Long, Geometry<?>> geometries = new HashMap<>();
    private final Map<Long, ImplicitGeometry> implicitGeometries = new HashMap<>();
    private final Map<Long, Appearance> appearances = new HashMap<>();
    private final Map<Long, Address> addresses = new HashMap<>();
    private final Map<Long, Property<?>> properties = new HashMap<>();

    Hierarchy() {
    }

    public Map<Long, Feature> getFeatures() {
        return features;
    }

    public Feature getFeature(Long id) {
        return id != null ? features.get(id) : null;
    }

    public void addFeature(long id, Feature feature) {
        if (feature != null) {
            features.put(id, feature);
        }
    }

    public Map<Long, Geometry<?>> getGeometries() {
        return geometries;
    }

    public Geometry<?> getGeometry(Long id) {
        return id != null ? geometries.get(id) : null;
    }

    public void addGeometry(long id, Geometry<?> geometry) {
        if (geometry != null) {
            geometries.put(id, geometry);
        }
    }

    public Map<Long, ImplicitGeometry> getImplicitGeometries() {
        return implicitGeometries;
    }

    public ImplicitGeometry getImplicitGeometry(Long id) {
        return id != null ? implicitGeometries.get(id) : null;
    }

    public void addImplicitGeometry(long id, ImplicitGeometry implicitGeometry) {
        if (implicitGeometry != null) {
            implicitGeometries.put(id, implicitGeometry);
        }
    }

    public Map<Long, Appearance> getAppearances() {
        return appearances;
    }

    public Appearance getAppearance(Long id) {
        return id != null ? appearances.get(id) : null;
    }

    public void addAppearance(long id, Appearance appearance) {
        if (appearance != null) {
            appearances.put(id, appearance);
        }
    }

    public Map<Long, Address> getAddresses() {
        return addresses;
    }

    public Address getAddress(Long id) {
        return id != null ? addresses.get(id) : null;
    }

    public void addAddress(long id, Address address) {
        if (address != null) {
            addresses.put(id, address);
        }
    }

    public Map<Long, Property<?>> getProperties() {
        return properties;
    }

    public Property<?> getProperty(Long id) {
        return id != null ? properties.get(id) : null;
    }

    public <T extends Property<?>> T getProperty(Long id, Class<T> type) {
        Property<?> property = getProperty(id);
        return type.isInstance(property) ? type.cast(property) : null;
    }

    public void addProperty(long id, Property<?> property) {
        if (property != null) {
            properties.put(id, property);
        }
    }
}
