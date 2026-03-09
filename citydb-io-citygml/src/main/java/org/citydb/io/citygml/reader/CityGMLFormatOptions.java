/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader;

import org.citydb.config.SerializableConfig;
import org.citydb.io.citygml.reader.options.FormatOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SerializableConfig(name = "CityGML")
public class CityGMLFormatOptions extends FormatOptions<CityGMLFormatOptions> {
    private boolean resolveCrossLodReferences = true;
    private boolean createCityObjectRelations = true;
    private boolean useLod4AsLod3;
    private boolean mapLod0RoofEdge;
    private boolean mapLod1MultiSurfaces;
    private boolean includeXALSource;
    private List<String> xslTransforms;

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

    public boolean hasXslTransforms() {
        return xslTransforms != null && !xslTransforms.isEmpty();
    }

    public List<String> getXslTransforms() {
        if (xslTransforms == null) {
            xslTransforms = new ArrayList<>();
        }

        return xslTransforms;
    }

    public CityGMLFormatOptions setXslTransforms(List<String> xslTransforms) {
        this.xslTransforms = xslTransforms;
        return this;
    }

    public CityGMLFormatOptions addXslTransform(Path xslTransform) {
        return addXslTransform(xslTransform != null ? xslTransform.toString() : null);
    }

    public CityGMLFormatOptions addXslTransform(String xslTransform) {
        if (xslTransform != null) {
            getXslTransforms().add(xslTransform);
        }

        return this;
    }

    @Override
    protected CityGMLFormatOptions self() {
        return this;
    }
}
