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

package org.citydb.io.citygml.adapter.core;

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractSpace;

import java.util.function.BiFunction;

public class SpaceGeometrySupport<T extends AbstractSpace> {
    private BiFunction<CityGMLVersion, T, Boolean> lod0PointSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod0MultiSurfaceSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod0MultiCurveSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod1SolidSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod2SolidSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod2MultiSurfaceSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod2MultiCurveSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod3SolidSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod3MultiSurfaceSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod3MultiCurveSupport;
    private BiFunction<CityGMLVersion, T, Boolean> lod1TerrainIntersectionCurve;
    private BiFunction<CityGMLVersion, T, Boolean> lod2TerrainIntersectionCurve;
    private BiFunction<CityGMLVersion, T, Boolean> lod3TerrainIntersectionCurve;
    private BiFunction<CityGMLVersion, T, Boolean> lod1ImplicitRepresentation;
    private BiFunction<CityGMLVersion, T, Boolean> lod2ImplicitRepresentation;
    private BiFunction<CityGMLVersion, T, Boolean> lod3ImplicitRepresentation;

    SpaceGeometrySupport() {
    }

    boolean supportsLod0Point(CityGMLVersion version, T target) {
        return lod0PointSupport != null && lod0PointSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod0Point() {
        return withLod0Point((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod0Point(BiFunction<CityGMLVersion, T, Boolean> lod0PointSupport) {
        this.lod0PointSupport = lod0PointSupport;
        return this;
    }

    boolean supportsLod0MultiSurface(CityGMLVersion version, T target) {
        return lod0MultiSurfaceSupport != null && lod0MultiSurfaceSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod0MultiSurface() {
        return withLod0MultiSurface((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod0MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod0MultiSurfaceSupport) {
        this.lod0MultiSurfaceSupport = lod0MultiSurfaceSupport;
        return this;
    }

    boolean supportsLod0MultiCurve(CityGMLVersion version, T target) {
        return lod0MultiCurveSupport != null && lod0MultiCurveSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod0MultiCurve() {
        return withLod0MultiCurve((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod0MultiCurve(BiFunction<CityGMLVersion, T, Boolean> lod0MultiCurveSupport) {
        this.lod0MultiCurveSupport = lod0MultiCurveSupport;
        return this;
    }

    boolean supportsLod1Solid(CityGMLVersion version, T target) {
        return lod1SolidSupport != null && lod1SolidSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod1Solid() {
        return withLod1Solid((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod1Solid(BiFunction<CityGMLVersion, T, Boolean> lod1SolidSupport) {
        this.lod1SolidSupport = lod1SolidSupport;
        return this;
    }

    boolean supportsLod2Solid(CityGMLVersion version, T target) {
        return lod2SolidSupport != null && lod2SolidSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod2Solid() {
        return withLod2Solid((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod2Solid(BiFunction<CityGMLVersion, T, Boolean> lod2SolidSupport) {
        this.lod2SolidSupport = lod2SolidSupport;
        return this;
    }

    boolean supportsLod2MultiSurface(CityGMLVersion version, T target) {
        return lod2MultiSurfaceSupport != null && lod2MultiSurfaceSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod2MultiSurface() {
        return withLod2MultiSurface((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod2MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod2MultiSurfaceSupport) {
        this.lod2MultiSurfaceSupport = lod2MultiSurfaceSupport;
        return this;
    }

    boolean supportsLod2MultiCurve(CityGMLVersion version, T target) {
        return lod2MultiCurveSupport != null && lod2MultiCurveSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod2MultiCurve() {
        return withLod2MultiCurve((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod2MultiCurve(BiFunction<CityGMLVersion, T, Boolean> lod2MultiCurveSupport) {
        this.lod2MultiCurveSupport = lod2MultiCurveSupport;
        return this;
    }

    boolean supportsLod3Solid(CityGMLVersion version, T target) {
        return lod3SolidSupport != null && lod3SolidSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod3Solid() {
        return withLod3Solid((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod3Solid(BiFunction<CityGMLVersion, T, Boolean> lod3SolidSupport) {
        this.lod3SolidSupport = lod3SolidSupport;
        return this;
    }

    boolean supportsLod3MultiSurface(CityGMLVersion version, T target) {
        return lod3MultiSurfaceSupport != null && lod3MultiSurfaceSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod3MultiSurface() {
        return withLod3MultiSurface((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod3MultiSurface(BiFunction<CityGMLVersion, T, Boolean> lod3MultiSurfaceSupport) {
        this.lod3MultiSurfaceSupport = lod3MultiSurfaceSupport;
        return this;
    }

    boolean supportsLod3MultiCurve(CityGMLVersion version, T target) {
        return lod3MultiCurveSupport != null && lod3MultiCurveSupport.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod3MultiCurve() {
        return withLod3MultiCurve((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod3MultiCurve(BiFunction<CityGMLVersion, T, Boolean> lod3MultiCurveSupport) {
        this.lod3MultiCurveSupport = lod3MultiCurveSupport;
        return this;
    }

    boolean supportsLod1TerrainIntersectionCurve(CityGMLVersion version, T target) {
        return lod1TerrainIntersectionCurve != null && lod1TerrainIntersectionCurve.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod1TerrainIntersectionCurve() {
        return withLod1TerrainIntersectionCurve((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod1TerrainIntersectionCurve(BiFunction<CityGMLVersion, T, Boolean> lod1TerrainIntersectionCurve) {
        this.lod1TerrainIntersectionCurve = lod1TerrainIntersectionCurve;
        return this;
    }

    boolean supportsLod2TerrainIntersectionCurve(CityGMLVersion version, T target) {
        return lod2TerrainIntersectionCurve != null && lod2TerrainIntersectionCurve.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod2TerrainIntersectionCurve() {
        return withLod2TerrainIntersectionCurve((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod2TerrainIntersectionCurve(BiFunction<CityGMLVersion, T, Boolean> lod2TerrainIntersectionCurve) {
        this.lod2TerrainIntersectionCurve = lod2TerrainIntersectionCurve;
        return this;
    }

    boolean supportsLod3TerrainIntersectionCurve(CityGMLVersion version, T target) {
        return lod3TerrainIntersectionCurve != null && lod3TerrainIntersectionCurve.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod3TerrainIntersectionCurve() {
        return withLod3TerrainIntersectionCurve((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod3TerrainIntersectionCurve(BiFunction<CityGMLVersion, T, Boolean> lod3TerrainIntersectionCurve) {
        this.lod3TerrainIntersectionCurve = lod3TerrainIntersectionCurve;
        return this;
    }

    boolean supportsLod1ImplicitRepresentation(CityGMLVersion version, T target) {
        return lod1ImplicitRepresentation != null && lod1ImplicitRepresentation.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod1ImplicitRepresentation() {
        return withLod1ImplicitRepresentation((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod1ImplicitRepresentation(BiFunction<CityGMLVersion, T, Boolean> lod1ImplicitRepresentation) {
        this.lod1ImplicitRepresentation = lod1ImplicitRepresentation;
        return this;
    }

    boolean supportsLod2ImplicitRepresentation(CityGMLVersion version, T target) {
        return lod2ImplicitRepresentation != null && lod2ImplicitRepresentation.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod2ImplicitRepresentation() {
        return withLod2ImplicitRepresentation((version, target) -> true);
    }

    public SpaceGeometrySupport<T> withLod2ImplicitRepresentation(BiFunction<CityGMLVersion, T, Boolean> lod2ImplicitRepresentation) {
        this.lod2ImplicitRepresentation = lod2ImplicitRepresentation;
        return this;
    }
    
    boolean supportsLod3ImplicitRepresentation(CityGMLVersion version, T target) {
        return lod3ImplicitRepresentation != null && lod3ImplicitRepresentation.apply(version, target);
    }

    public SpaceGeometrySupport<T> withLod3ImplicitRepresentation() {
        return withLod3ImplicitRepresentation((version, target) -> true);
    }
    
    public SpaceGeometrySupport<T> withLod3ImplicitRepresentation(BiFunction<CityGMLVersion, T, Boolean> lod3ImplicitRepresentation) {
        this.lod3ImplicitRepresentation = lod3ImplicitRepresentation;
        return this;
    }
}
