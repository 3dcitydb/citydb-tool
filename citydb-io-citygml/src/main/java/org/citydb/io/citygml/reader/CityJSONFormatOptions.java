/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.citygml.reader;

import org.citydb.config.SerializableConfig;
import org.citydb.io.citygml.reader.options.FormatOptions;

@SerializableConfig(name = "CityJSON")
public class CityJSONFormatOptions extends FormatOptions<CityJSONFormatOptions> {
    private boolean mapUnsupportedTypesToGenerics = true;

    public boolean isMapUnsupportedTypesToGenerics() {
        return mapUnsupportedTypesToGenerics;
    }

    public CityJSONFormatOptions setMapUnsupportedTypesToGenerics(boolean mapUnsupportedTypesToGenerics) {
        this.mapUnsupportedTypesToGenerics = mapUnsupportedTypesToGenerics;
        return this;
    }

    @Override
    protected CityJSONFormatOptions self() {
        return this;
    }
}
