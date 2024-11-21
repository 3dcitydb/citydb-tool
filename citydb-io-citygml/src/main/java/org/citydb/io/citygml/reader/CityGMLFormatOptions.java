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

@SerializableConfig(name = "CityGML")
public class CityGMLFormatOptions extends FormatOptions<CityGMLFormatOptions> {
    private boolean resolveGeometryReferences = true;
    private boolean resolveCrossLodReferences = true;
    private boolean createCityObjectRelations = true;
    private boolean useLod4AsLod3;
    private boolean mapLod0RoofEdge;
    private boolean mapLod1MultiSurfaces;
    private boolean includeXALSource;

    public boolean isResolveGeometryReferences() {
        return resolveGeometryReferences;
    }

    public CityGMLFormatOptions setResolveGeometryReferences(boolean resolveGeometryReferences) {
        this.resolveGeometryReferences = resolveGeometryReferences;
        return this;
    }

    public boolean isResolveCrossLodReferences() {
        return resolveCrossLodReferences;
    }

    public CityGMLFormatOptions setResolveCrossLodReferences(boolean resolveCrossLodReferences) {
        this.resolveCrossLodReferences = resolveCrossLodReferences;
        return this;
    }

    public boolean isCreateCityObjectRelations() {
        return createCityObjectRelations;
    }

    public CityGMLFormatOptions setCreateCityObjectRelations(boolean createCityObjectRelations) {
        this.createCityObjectRelations = createCityObjectRelations;
        return this;
    }

    public boolean isUseLod4AsLod3() {
        return useLod4AsLod3;
    }

    public CityGMLFormatOptions setUseLod4AsLod3(boolean useLod4AsLod3) {
        this.useLod4AsLod3 = useLod4AsLod3;
        return this;
    }

    public boolean isMapLod0RoofEdge() {
        return mapLod0RoofEdge;
    }

    public CityGMLFormatOptions setMapLod0RoofEdge(boolean mapLod0RoofEdge) {
        this.mapLod0RoofEdge = mapLod0RoofEdge;
        return this;
    }

    public boolean isMapLod1MultiSurfaces() {
        return mapLod1MultiSurfaces;
    }

    public CityGMLFormatOptions setMapLod1MultiSurfaces(boolean mapLod1MultiSurfaces) {
        this.mapLod1MultiSurfaces = mapLod1MultiSurfaces;
        return this;
    }

    public boolean isIncludeXALSource() {
        return includeXALSource;
    }

    public CityGMLFormatOptions setIncludeXALSource(boolean includeXALSource) {
        this.includeXALSource = includeXALSource;
        return this;
    }

    @Override
    protected CityGMLFormatOptions self() {
        return this;
    }
}
