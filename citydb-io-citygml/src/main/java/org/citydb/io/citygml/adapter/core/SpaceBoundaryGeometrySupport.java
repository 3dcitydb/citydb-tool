/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.io.citygml.adapter.core;

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractSpaceBoundary;

import java.util.function.BiFunction;

public class SpaceBoundaryGeometrySupport<T extends AbstractSpaceBoundary> {
    private BiFunction<CityGMLVersion, T, Boolean> lod0MultiCurveSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod0MultiSurfaceSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod1MultiSurfaceSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod2MultiSurfaceSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod3MultiSurfaceSupport;

    SpaceBoundaryGeometrySupport() {
    }

    boolean supportsLod0MultiCurve(CityGMLVersion version, T target) {
        return lod0MultiCurveSupport != null && lod0MultiCurveSupport.apply(version, target);
    }

    public SpaceBoundaryGeometrySupport<T> withLod0MultiCurve() {
        return withLod0MultiCurve((version, target) -> true);
    }

    public SpaceBoundaryGeometrySupport<T> withLod0MultiCurve(BiFunction<CityGMLVersion, T, Boolean> lod0MultiCurveSupport) {
        this.lod0MultiCurveSupport = lod0MultiCurveSupport;
        return this;
    }

    boolean supportsLod0MultiSurface(CityGMLVersion version, T target) {
        return lod0MultiSurfaceSupport != null && lod0MultiSurfaceSupport.apply(version, target);
    }

    public SpaceBoundaryGeometrySupport<T> withLod0MultiSurface() {
        return withLod0MultiSurface((version, target) -> true);
    }

    public SpaceBoundaryGeometrySupport<T> withLod0MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod0MultiSurfaceSupport) {
        this.lod0MultiSurfaceSupport = lod0MultiSurfaceSupport;
        return this;
    }

    boolean supportsLod1MultiSurface(CityGMLVersion version, T target) {
        return lod1MultiSurfaceSupport != null && lod1MultiSurfaceSupport.apply(version, target);
    }

    public SpaceBoundaryGeometrySupport<T> withLod1MultiSurface() {
        return withLod1MultiSurface((version, target) -> true);
    }

    public SpaceBoundaryGeometrySupport<T> withLod1MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod1MultiSurfaceSupport) {
        this.lod1MultiSurfaceSupport = lod1MultiSurfaceSupport;
        return this;
    }

    boolean supportsLod2MultiSurface(CityGMLVersion version, T target) {
        return lod2MultiSurfaceSupport != null && lod2MultiSurfaceSupport.apply(version, target);
    }

    public SpaceBoundaryGeometrySupport<T> withLod2MultiSurface() {
        return withLod2MultiSurface((version, target) -> true);
    }

    public SpaceBoundaryGeometrySupport<T> withLod2MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod2MultiSurfaceSupport) {
        this.lod2MultiSurfaceSupport = lod2MultiSurfaceSupport;
        return this;
    }

    boolean supportsLod3MultiSurface(CityGMLVersion version, T target) {
        return lod3MultiSurfaceSupport != null && lod3MultiSurfaceSupport.apply(version, target);
    }

    public SpaceBoundaryGeometrySupport<T> withLod3MultiSurface() {
        return withLod3MultiSurface((version, target) -> true);
    }

    public SpaceBoundaryGeometrySupport<T> withLod3MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod3MultiSurfaceSupport) {
        this.lod3MultiSurfaceSupport = lod3MultiSurfaceSupport;
        return this;
    }
}
