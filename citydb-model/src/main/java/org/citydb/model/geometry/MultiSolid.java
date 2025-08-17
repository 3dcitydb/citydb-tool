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

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Collections;
import java.util.List;

public class MultiSolid extends SolidCollection<MultiSolid> {

    private MultiSolid(List<Solid> solids) {
        super(solids);
    }

    private MultiSolid(Solid... solids) {
        super(solids);
    }

    public static MultiSolid of(List<Solid> solids) {
        return new MultiSolid(solids);
    }

    public static MultiSolid of(Solid... solids) {
        return new MultiSolid(solids);
    }

    public static MultiSolid empty() {
        return new MultiSolid(Collections.emptyList());
    }

    @Override
    public MultiSolid copy() {
        return new MultiSolid(getSolids().stream()
                .map(Solid::copy)
                .toArray(Solid[]::new))
                .copyPropertiesFrom(this);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.MULTI_SOLID;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    MultiSolid self() {
        return this;
    }
}
