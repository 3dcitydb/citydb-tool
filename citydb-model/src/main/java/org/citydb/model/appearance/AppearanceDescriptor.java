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
