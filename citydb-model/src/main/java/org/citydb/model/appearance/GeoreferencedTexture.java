/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import org.citydb.model.common.*;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Surface;
import org.citydb.model.util.CopySession;

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
    protected GeoreferencedTexture createClone(CopySession session) {
        return new GeoreferencedTexture();
    }

    @Override
    protected void copyPropertiesTo(Child clone, CopySession session) {
        GeoreferencedTexture texture = (GeoreferencedTexture) clone;
        super.copyPropertiesTo(texture, session);

        texture.referencePoint = texture.asChild(copy(referencePoint, session));
        texture.orientation = orientation != null ? orientation.copy() : null;

        if (targets != null) {
            texture.targets = new ArrayList<>(targets.size());
            for (Surface<?> target : targets) {
                texture.targets.add(copy(target, session));
            }
        }
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
