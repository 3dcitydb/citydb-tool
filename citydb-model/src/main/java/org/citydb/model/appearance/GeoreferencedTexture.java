/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import org.citydb.model.common.Matrix2x2;
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
    private Matrix2x2 orientation;
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

    public Optional<Matrix2x2> getOrientation() {
        return Optional.ofNullable(orientation);
    }

    public GeoreferencedTexture setOrientation(Matrix2x2 orientation) {
        this.orientation = orientation;
        return this;
    }

    public GeoreferencedTexture setOrientation(List<Double> orientation) {
        if (orientation != null && orientation.size() > 3) {
            this.orientation = Matrix2x2.ofRowMajor(orientation);
        }

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
