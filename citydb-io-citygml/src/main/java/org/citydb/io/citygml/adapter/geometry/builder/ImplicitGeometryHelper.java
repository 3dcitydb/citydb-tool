/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.builder;

import org.citydb.io.citygml.adapter.appearance.builder.AppearanceHelper;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.reader.preprocess.ImplicitGeometryResolver;
import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.slf4j.event.Level;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.Envelope;

import java.io.IOException;

public class ImplicitGeometryHelper {
    private final ImplicitGeometryResolver resolver;
    private final ModelBuilderHelper helper;

    public ImplicitGeometryHelper(ImplicitGeometryResolver resolver, ModelBuilderHelper helper) {
        this.resolver = resolver;
        this.helper = helper;
    }

    public boolean hasImplicitGeometries() {
        return resolver.hasImplicitGeometries();
    }

    public Envelope computeEnvelope(org.citygml4j.core.model.core.ImplicitGeometry implicitGeometry) {
        return resolver.computeEnvelope(implicitGeometry);
    }

    public ImplicitGeometryProperty getImplicitGeometry(Name name, org.citygml4j.core.model.core.ImplicitGeometry source, boolean force2D) throws ModelBuildException {
        if (source != null) {
            if (source.getRelativeGeometry() != null) {
                if (source.getRelativeGeometry().getHref() != null) {
                    String objectId = FeatureHelper.getIdFromReference(source.getRelativeGeometry().getHref());
                    if (helper.lookupAndPut(source.getRelativeGeometry())) {
                        return ImplicitGeometryProperty.of(name, objectId);
                    } else {
                        ImplicitGeometry target = resolver.getOrConvert(objectId,
                                implicitGeometry -> buildImplicitGeometry(implicitGeometry, force2D));
                        if (target != null) {
                            return ImplicitGeometryProperty.of(name, target);
                        }
                    }
                } else {
                    ImplicitGeometry target = buildImplicitGeometry(source, force2D);
                    if (target != null) {
                        return ImplicitGeometryProperty.of(name, target);
                    }
                }
            } else if (source.getLibraryObject() != null) {
                try {
                    ExternalFile libraryObject = helper.getExternalFile(source.getLibraryObject());
                    return helper.lookupAndPut(libraryObject) ?
                            ImplicitGeometryProperty.of(name, libraryObject.getOrCreateObjectId()) :
                            ImplicitGeometryProperty.of(name, ImplicitGeometry.of(libraryObject));
                } catch (IOException e) {
                    helper.logOrThrow(Level.ERROR, helper.formatMessage(source, "Failed to read library object file " +
                            source.getLibraryObject() + "."), e);
                }
            }
        }

        return null;
    }

    private ImplicitGeometry buildImplicitGeometry(org.citygml4j.core.model.core.ImplicitGeometry source, boolean force2D) throws ModelBuildException {
        if (source.getRelativeGeometry() != null && source.getRelativeGeometry().getObject() != null) {
            AbstractGeometry template = source.getRelativeGeometry().getObject();
            AppearanceHelper appearanceHelper = new AppearanceHelper(helper);
            GeometryHelper geometryHelper = new GeometryHelper(appearanceHelper, helper);

            Geometry<?> geometry = geometryHelper.getGeometry(template, force2D);
            if (geometry != null) {
                ImplicitGeometry target = ImplicitGeometry.of(geometry.setSrsIdentifier(template.getSrsName()));
                if (source.isSetAppearances()) {
                    for (AbstractAppearanceProperty property : source.getAppearances()) {
                        if (property != null && property.getObject() != null) {
                            Appearance appearance = appearanceHelper.getAppearance(property.getObject());
                            if (appearance != null) {
                                target.addAppearance(AppearanceProperty.of(
                                        Name.of("appearance", Namespaces.CORE), appearance));
                            }
                        }
                    }

                    if (target.hasAppearances()) {
                        appearanceHelper.processTargets(source);
                    }
                }

                return target;
            }
        }

        return null;
    }
}
