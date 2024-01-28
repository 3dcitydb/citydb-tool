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

package org.citydb.model.appearance;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.Visitor;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GeoreferencedTexture extends Texture<GeoreferencedTexture> {
    private Point referencePoint;
    private List<Double> orientation;
    private List<Surface<?>> targets;

    private GeoreferencedTexture() {
    }

    public static GeoreferencedTexture newInstance() {
        return new GeoreferencedTexture();
    }

    @Override
    public Name getName() {
        return Name.of("GeoreferencedTexture", Namespaces.APPEARANCE);
    }

    public Optional<Point> getReferencePoint() {
        return Optional.ofNullable(referencePoint);
    }

    public GeoreferencedTexture setReferencePoint(Point referencePoint) {
        this.referencePoint = referencePoint;
        return this;
    }

    public Optional<List<Double>> getOrientation() {
        return Optional.ofNullable(orientation);
    }

    public GeoreferencedTexture setOrientation(List<Double> orientation) {
        this.orientation = orientation != null && orientation.size() > 3 ?
                new ArrayList<>(orientation.subList(0, 4)) :
                null;

        return this;
    }

    public boolean hasTargets() {
        return targets != null && !targets.isEmpty();
    }

    public List<Surface<?>> getTargets() {
        if (targets == null) {
            targets = new ArrayList<>();
        }

        return targets;
    }

    public GeoreferencedTexture setTargets(List<Surface<?>> targets) {
        this.targets = targets;
        return this;
    }

    public GeoreferencedTexture addTarget(Surface<?> target) {
        if (target != null) {
            getTargets().add(target);
        }

        return this;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    GeoreferencedTexture self() {
        return this;
    }
}
