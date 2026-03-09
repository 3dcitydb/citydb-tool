/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.Child;
import org.citydb.model.common.InlineProperty;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;

import java.util.Objects;

public class AppearanceProperty extends Property<AppearanceProperty> implements InlineProperty<Appearance> {
    private Appearance appearance;

    private AppearanceProperty(Name name, Appearance appearance) {
        super(name, DataType.APPEARANCE_PROPERTY);
        Objects.requireNonNull(appearance, "The appearance must not be null.");
        this.appearance = asChild(appearance);
    }

    public static AppearanceProperty of(Name name, Appearance appearance) {
        return new AppearanceProperty(name, appearance);
    }

    @Override
    public Appearance getObject() {
        return appearance;
    }

    @Override
    public AppearanceProperty setObject(Appearance appearance) {
        if (appearance != null) {
            this.appearance = asChild(appearance);
        }

        return this;
    }

    @Override
    public boolean removeFromParent() {
        Child parent = getParent().orElse(null);
        if (parent instanceof Feature feature) {
            return feature.getAppearances().remove(this);
        } else if (parent instanceof ImplicitGeometry geometry) {
            return geometry.getAppearances().remove(this);
        } else if (parent instanceof Attribute attribute) {
            return attribute.getProperties().remove(this);
        } else {
            return false;
        }
    }

    @Override
    AppearanceProperty self() {
        return this;
    }
}
