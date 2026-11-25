/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.model.address.AddressDescriptor;
import org.citydb.model.appearance.AppearanceDescriptor;
import org.citydb.model.change.FeatureChangeDescriptor;
import org.citydb.model.feature.FeatureDescriptor;
import org.citydb.model.geometry.GeometryDescriptor;
import org.citydb.model.property.PropertyDescriptor;

import java.io.Serializable;

public sealed abstract class DatabaseDescriptor implements Serializable
        permits AddressDescriptor, AppearanceDescriptor, FeatureChangeDescriptor, FeatureDescriptor, GeometryDescriptor,
        PropertyDescriptor {
    private final long id;

    public abstract DatabaseDescriptor copy();

    public DatabaseDescriptor(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
