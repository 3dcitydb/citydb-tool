/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.TransformationMatrix4x4;
import org.xmlobjects.gml.model.geometry.primitives.PointProperty;

public class ImplicitGeometryAdapter implements ModelBuilder<org.citygml4j.core.model.core.ImplicitGeometry, ImplicitGeometryProperty>, ModelSerializer<ImplicitGeometryProperty, org.citygml4j.core.model.core.ImplicitGeometry> {

    @Override
    public void build(org.citygml4j.core.model.core.ImplicitGeometry source, ImplicitGeometryProperty target, ModelBuilderHelper helper) throws ModelBuildException {
        if (source.getTransformationMatrix() != null) {
            target.setTransformationMatrix(source.getTransformationMatrix().toRowMajor());
        }

        if (source.getReferencePoint() != null) {
            target.setReferencePoint(helper.getPointGeometry(source.getReferencePoint().getObject(), Point.class));
        }

        ImplicitGeometry implicitGeometry = target.getObject().orElse(null);
        if (implicitGeometry != null && source.isSetAppearances()) {
            for (AbstractAppearanceProperty property : source.getAppearances()) {
                helper.addAppearance(Name.of("appearance", Namespaces.CORE), property, implicitGeometry);
            }
        }
    }

    @Override
    public org.citygml4j.core.model.core.ImplicitGeometry createObject(ImplicitGeometryProperty source) throws ModelSerializeException {
        return new org.citygml4j.core.model.core.ImplicitGeometry();
    }

    @Override
    public void serialize(ImplicitGeometryProperty source, org.citygml4j.core.model.core.ImplicitGeometry target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getTransformationMatrix().ifPresent(transformationMatrix ->
                target.setTransformationMatrix(TransformationMatrix4x4.ofRowMajor(
                        transformationMatrix.toRowMajor())));

        source.getReferencePoint().ifPresent(referencePoint ->
                target.setReferencePoint(new PointProperty(helper.getPoint(referencePoint))));

        ImplicitGeometry implicitGeometry = helper.getOrLookupObject(source);
        if (implicitGeometry != null && implicitGeometry.hasAppearances()) {
            for (AppearanceProperty property : implicitGeometry.getAppearances().getAll()) {
                boolean isInline = target.getRelativeGeometry() != null
                        && target.getRelativeGeometry().isSetInlineObject()
                        && !helper.lookupAndPut(property.getObject());
                if (helper.getCityGMLVersion() == CityGMLVersion.v3_0) {
                    target.getAppearances().add(isInline ?
                            new AbstractAppearanceProperty(helper.getAppearance(property.getObject())) :
                            new AbstractAppearanceProperty("#" + property.getObject().getOrCreateObjectId()));
                } else if (isInline) {
                    helper.writeAsGlobalFeature(helper.getAppearance(property.getObject()));
                }
            }
        }
    }
}
