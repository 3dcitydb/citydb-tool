/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.model.property;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.InlineProperty;
import org.citydb.model.common.Name;

import java.util.Objects;

public class AppearanceProperty extends Property<AppearanceProperty> implements InlineProperty<Appearance> {
    private final Appearance appearance;

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
    AppearanceProperty self() {
        return this;
    }
}
