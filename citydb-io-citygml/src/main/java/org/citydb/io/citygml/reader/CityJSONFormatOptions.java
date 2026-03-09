/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
