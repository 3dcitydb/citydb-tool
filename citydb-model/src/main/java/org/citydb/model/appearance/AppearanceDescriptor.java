/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import org.citydb.model.common.DatabaseDescriptor;

public class AppearanceDescriptor extends DatabaseDescriptor {
    private long featureId;
    private long implicitGeometryId;

    private AppearanceDescriptor(long id) {
        super(id);
    }

    public static AppearanceDescriptor of(long id) {
        return new AppearanceDescriptor(id);
    }

    public long getFeatureId() {
        return featureId;
    }

    public AppearanceDescriptor setFeatureId(long featureId) {
        this.featureId = featureId;
        return this;
    }

    public long getImplicitGeometryId() {
        return implicitGeometryId;
    }

    public AppearanceDescriptor setImplicitGeometryId(long implicitGeometryId) {
        this.implicitGeometryId = implicitGeometryId;
        return this;
    }
}
