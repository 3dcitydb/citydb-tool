/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader;

import org.citydb.config.SerializableConfig;
import org.citydb.io.citygml.reader.options.FormatOptions;
import org.citydb.io.citygml.reader.options.LodMappingOptions;

import java.util.Optional;

@SerializableConfig(name = "CityJSON")
public class CityJSONFormatOptions extends FormatOptions<CityJSONFormatOptions> {
    private boolean mapUnsupportedTypesToGenerics = true;
    private LodMappingOptions lodMapping;

    public boolean isMapUnsupportedTypesToGenerics() {
        return mapUnsupportedTypesToGenerics;
    }

    public CityJSONFormatOptions setMapUnsupportedTypesToGenerics(boolean mapUnsupportedTypesToGenerics) {
        this.mapUnsupportedTypesToGenerics = mapUnsupportedTypesToGenerics;
        return this;
    }

    public Optional<LodMappingOptions> getLodMapping() {
        return Optional.ofNullable(lodMapping);
    }

    public CityJSONFormatOptions setLodMapping(LodMappingOptions lodMapping) {
        this.lodMapping = lodMapping;
        return this;
    }

    @Override
    protected CityJSONFormatOptions self() {
        return this;
    }
}
