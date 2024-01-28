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

package org.citydb.io.citygml.adapter.geometry.builder;

import org.apache.logging.log4j.Level;
import org.citydb.io.citygml.adapter.appearance.builder.AppearanceHelper;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Name;
import org.citydb.model.common.Reference;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import java.io.IOException;

public class GeometryHelper {
    private final ModelBuilderHelper helper;
    private final PointGeometryBuilder pointGeometryBuilder;
    private final CurveGeometryBuilder curveGeometryBuilder;
    private final SurfaceGeometryBuilder surfaceGeometryBuilder;
    private final SolidGeometryBuilder solidGeometryBuilder;

    public GeometryHelper(AppearanceHelper appearanceHelper, ModelBuilderHelper helper) {
        this.helper = helper;
        pointGeometryBuilder = new PointGeometryBuilder();
        curveGeometryBuilder = new CurveGeometryBuilder();
        surfaceGeometryBuilder = new SurfaceGeometryBuilder(appearanceHelper);
        solidGeometryBuilder = new SolidGeometryBuilder(surfaceGeometryBuilder);
    }

    public Geometry<?> getPointGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return buildGeometry(source, force2D, pointGeometryBuilder);
    }

    public <T extends Geometry<?>> T getPointGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return getGeometry(getPointGeometry(source, force2D), type);
    }

    public Geometry<?> getPointGeometry(GeometryProperty<?> source, boolean force2D) throws ModelBuildException {
        return source != null ? getPointGeometry(source.getObject(), force2D) : null;
    }

    public Geometry<?> getCurveGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return buildGeometry(source, force2D, curveGeometryBuilder);
    }

    public <T extends Geometry<?>> T getCurveGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return getGeometry(getCurveGeometry(source, force2D), type);
    }

    public Geometry<?> getCurveGeometry(GeometryProperty<?> source, boolean force2D) throws ModelBuildException {
        return source != null ? getCurveGeometry(source.getObject(), force2D) : null;
    }

    public Geometry<?> getSurfaceGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        return buildGeometry(source, force2D, surfaceGeometryBuilder);
    }

    public <T extends Geometry<?>> T getSurfaceGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return getGeometry(getSurfaceGeometry(source, force2D), type);
    }

    public Geometry<?> getSurfaceGeometry(GeometryProperty<?> source, boolean force2D) throws ModelBuildException {
        return source != null ? getSurfaceGeometry(source.getObject(), force2D) : null;
    }

    public Geometry<?> getSolidGeometry(AbstractGeometry source) throws ModelBuildException {
        return buildGeometry(source, false, solidGeometryBuilder);
    }

    public <T extends Geometry<?>> T getSolidGeometry(AbstractGeometry source, Class<T> type) throws ModelBuildException {
        return getGeometry(getSolidGeometry(source), type);
    }

    public Geometry<?> getSolidGeometry(GeometryProperty<?> source) throws ModelBuildException {
        return source != null ? getSolidGeometry(source.getObject()) : null;
    }

    public Geometry<?> getGeometry(AbstractGeometry source, boolean force2D) throws ModelBuildException {
        Geometry<?> geometry = getSolidGeometry(source);
        if (geometry == null) {
            geometry = getSurfaceGeometry(source, force2D);
        }

        if (geometry == null) {
            geometry = getCurveGeometry(source, force2D);
        }

        if (geometry == null) {
            geometry = getPointGeometry(source, force2D);
        }

        return geometry;
    }

    public <T extends Geometry<?>> T getGeometry(AbstractGeometry source, boolean force2D, Class<T> type) throws ModelBuildException {
        return getGeometry(getGeometry(source, force2D), type);
    }

    public Geometry<?> getGeometry(GeometryProperty<?> source, boolean force2D) throws ModelBuildException {
        return source != null ? getGeometry(source.getObject(), force2D) : null;
    }

    private <T extends Geometry<?>> T getGeometry(Geometry<?> geometry, Class<T> type) {
        return type.isInstance(geometry) ? type.cast(geometry) : null;
    }

    private Geometry<?> buildGeometry(AbstractGeometry source, boolean force2D, GeometryBuilder builder) throws ModelBuildException {
        if (source != null) {
            Geometry<?> geometry = builder.build(source);
            if (geometry != null) {
                geometry.setObjectId(source.getId())
                        .setSrsName(helper.getInheritedSrsName(source));
                if (force2D) {
                    geometry.force2D();
                }
            }

            return geometry;
        }

        return null;
    }

    public ImplicitGeometryProperty getImplicitGeometry(org.citygml4j.core.model.core.ImplicitGeometry source, Name name, boolean force2D) throws ModelBuildException {
        if (source != null) {
            if (source.getRelativeGeometry() != null) {
                if (source.getRelativeGeometry().isSetInlineObject()) {
                    Geometry<?> geometry = getGeometry(source.getRelativeGeometry().getObject(), force2D);
                    if (geometry != null) {
                        return ImplicitGeometryProperty.of(name, ImplicitGeometry.of(geometry
                                .setSrsName(source.getRelativeGeometry().getObject().getSrsName())));
                    }
                } else if (source.getRelativeGeometry().getHref() != null) {
                    return ImplicitGeometryProperty.of(name, Reference.of(
                            helper.getIdFromReference(source.getRelativeGeometry().getHref())));
                }
            } else if (source.getLibraryObject() != null) {
                try {
                    ExternalFile libraryObject = helper.getExternalFile(source.getLibraryObject());
                    return helper.lookupAndPut(libraryObject) ?
                            ImplicitGeometryProperty.of(name, Reference.of(libraryObject.getOrCreateObjectId())) :
                            ImplicitGeometryProperty.of(name, ImplicitGeometry.of(libraryObject));
                } catch (IOException e) {
                    helper.logOrThrow(Level.ERROR, helper.formatMessage(source, "Failed to read library object file " +
                            source.getLibraryObject() + "."), e);
                }
            }
        }

        return null;
    }

    public ImplicitGeometryProperty getImplicitGeometry(org.citygml4j.core.model.core.ImplicitGeometryProperty source, Name name, boolean force2D) throws ModelBuildException {
        return source != null ? getImplicitGeometry(source.getObject(), name, force2D) : null;
    }
}
