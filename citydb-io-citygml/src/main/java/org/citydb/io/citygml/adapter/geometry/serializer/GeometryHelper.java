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

package org.citydb.io.citygml.adapter.geometry.serializer;


import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Reference;
import org.citydb.model.geometry.*;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.xmlobjects.gml.model.basictypes.Sign;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.DirectPositionList;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiCurve;
import org.xmlobjects.gml.model.geometry.aggregates.MultiPoint;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSolid;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.complexes.CompositeSolid;
import org.xmlobjects.gml.model.geometry.complexes.CompositeSurface;
import org.xmlobjects.gml.model.geometry.primitives.LineString;
import org.xmlobjects.gml.model.geometry.primitives.LinearRing;
import org.xmlobjects.gml.model.geometry.primitives.Point;
import org.xmlobjects.gml.model.geometry.primitives.Polygon;
import org.xmlobjects.gml.model.geometry.primitives.Solid;
import org.xmlobjects.gml.model.geometry.primitives.TriangulatedSurface;
import org.xmlobjects.gml.model.geometry.primitives.*;

import java.util.List;

public class GeometryHelper {
    private final ModelSerializerHelper helper;

    public GeometryHelper(ModelSerializerHelper helper) {
        this.helper = helper;
    }

    public AbstractGeometry getGeometry(Geometry<?> source, boolean force3D) {
        if (source != null) {
            int dimension = getDimension(source, force3D);
            return switch (source.getGeometryType()) {
                case POINT -> getPoint((org.citydb.model.geometry.Point) source, dimension);
                case MULTI_POINT -> getMultiPoint((org.citydb.model.geometry.MultiPoint) source, dimension);
                case LINE_STRING -> getLineString((org.citydb.model.geometry.LineString) source, dimension);
                case MULTI_LINE_STRING -> getMultiCurve((MultiLineString) source, dimension);
                case POLYGON -> getPolygon((org.citydb.model.geometry.Polygon) source, dimension);
                case COMPOSITE_SURFACE ->
                        getCompositeSurface((org.citydb.model.geometry.CompositeSurface) source, dimension);
                case TRIANGULATED_SURFACE ->
                        getTriangulatedSurface((org.citydb.model.geometry.TriangulatedSurface) source, dimension);
                case MULTI_SURFACE -> getMultiSurface((org.citydb.model.geometry.MultiSurface) source, dimension);
                case SOLID -> getSolid((org.citydb.model.geometry.Solid) source);
                case COMPOSITE_SOLID -> getCompositeSolid((org.citydb.model.geometry.CompositeSolid) source);
                case MULTI_SOLID -> getMultiSolid((org.citydb.model.geometry.MultiSolid) source);
            };
        }

        return null;
    }

    public Point getPoint(org.citydb.model.geometry.Point source, boolean force3D) {
        return getPoint(source, getDimension(source, force3D));
    }

    private Point getPoint(org.citydb.model.geometry.Point source, int dimension) {
        if (source != null) {
            Point target = new Point(getPosition(source.getCoordinate(), dimension));
            source.getObjectId().ifPresent(target::setId);
            return target;
        }

        return null;
    }

    public MultiPoint getMultiPoint(org.citydb.model.geometry.MultiPoint source, boolean force3D) {
        return getMultiPoint(source, getDimension(source, force3D));
    }

    private MultiPoint getMultiPoint(org.citydb.model.geometry.MultiPoint source, int dimension) {
        if (source != null) {
            MultiPoint target = new MultiPoint();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.Point point : source.getPoints()) {
                target.getPointMember().add(getPointProperty(point, dimension));
            }

            return target;
        }

        return null;
    }

    public LineString getLineString(org.citydb.model.geometry.LineString source, boolean force3D) {
        return getLineString(source, getDimension(source, force3D));
    }

    private LineString getLineString(org.citydb.model.geometry.LineString source, int dimension) {
        if (source != null) {
            LineString target = new LineString(getPositionList(source.getPoints(), dimension));
            source.getObjectId().ifPresent(target::setId);
            return target;
        }

        return null;
    }

    public MultiCurve getMultiCurve(MultiLineString source, boolean force3D) {
        return getMultiCurve(source, getDimension(source, force3D));
    }

    private MultiCurve getMultiCurve(MultiLineString source, int dimension) {
        if (source != null) {
            MultiCurve target = new MultiCurve();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.LineString lineString : source.getLineStrings()) {
                target.getCurveMember().add(getCurveProperty(lineString, dimension));
            }

            return target;
        }

        return null;
    }

    public Polygon getPolygon(org.citydb.model.geometry.Polygon source, boolean force3D) {
        return getPolygon(source, getDimension(source, force3D));
    }

    private Polygon getPolygon(org.citydb.model.geometry.Polygon source, int dimension) {
        if (source != null) {
            Polygon target = new Polygon(getLinearRing(source.getExteriorRing(), dimension));
            source.getObjectId().ifPresent(target::setId);
            if (source.hasInteriorRings()) {
                for (org.citydb.model.geometry.LinearRing ring : source.getInteriorRings()) {
                    target.getInterior().add(new AbstractRingProperty(getLinearRing(ring, dimension)));
                }
            }

            return target;
        }

        return null;
    }

    public CompositeSurface getCompositeSurface(org.citydb.model.geometry.CompositeSurface source, boolean force3D) {
        return getCompositeSurface(source, getDimension(source, force3D));
    }

    private CompositeSurface getCompositeSurface(org.citydb.model.geometry.CompositeSurface source, int dimension) {
        if (source != null) {
            CompositeSurface target = new CompositeSurface();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.Polygon polygon : source.getPolygons()) {
                target.getSurfaceMembers().add(getSurfaceProperty(polygon, dimension));
            }

            return target;
        }

        return null;
    }

    public TriangulatedSurface getTriangulatedSurface(org.citydb.model.geometry.TriangulatedSurface source, boolean force3D) {
        return getTriangulatedSurface(source, getDimension(source, force3D));
    }

    private TriangulatedSurface getTriangulatedSurface(org.citydb.model.geometry.TriangulatedSurface source, int dimension) {
        if (source != null) {
            TriangulatedSurface target = new TriangulatedSurface();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.Polygon polygon : source.getPolygons()) {
                target.addPatch(getTriangle(polygon, dimension));
            }

            return target;
        }

        return null;
    }

    public MultiSurface getMultiSurface(SurfaceCollection<?> source, boolean force3D) {
        return getMultiSurface(source, getDimension(source, force3D));
    }

    private MultiSurface getMultiSurface(SurfaceCollection<?> source, int dimension) {
        if (source != null) {
            MultiSurface target = new MultiSurface();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.Polygon polygon : source.getPolygons()) {
                target.getSurfaceMember().add(getSurfaceProperty(polygon, dimension));
            }

            return target;
        }

        return null;
    }

    public Solid getSolid(org.citydb.model.geometry.Solid source) {
        if (source != null) {
            Solid target = new Solid(getShell(source.getShell()));
            source.getObjectId().ifPresent(target::setId);
            return target;
        }

        return null;
    }

    public CompositeSolid getCompositeSolid(org.citydb.model.geometry.CompositeSolid source) {
        if (source != null) {
            CompositeSolid target = new CompositeSolid();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.Solid solid : source.getSolids()) {
                target.getSolidMembers().add(getSolidProperty(solid));
            }

            return target;
        }

        return null;
    }

    public MultiSolid getMultiSolid(SolidCollection<?> source) {
        if (source != null) {
            MultiSolid target = new MultiSolid();
            source.getObjectId().ifPresent(target::setId);
            for (org.citydb.model.geometry.Solid solid : source.getSolids()) {
                target.getSolidMember().add(getSolidProperty(solid));
            }

            return target;
        }

        return null;
    }

    public ImplicitGeometry getImplicitGeometry(ImplicitGeometryProperty source, boolean force3D) {
        if (source != null) {
            ImplicitGeometry target = new ImplicitGeometry();
            org.citydb.model.geometry.ImplicitGeometry implicitGeometry = source.getObject().orElse(null);
            if (implicitGeometry != null) {
                Geometry<?> geometry = implicitGeometry.getGeometry().orElse(null);
                if (geometry != null) {
                    target.setRelativeGeometry(helper.lookupAndPut(implicitGeometry) ?
                            new GeometryProperty<>("#" + geometry.getOrCreateObjectId()) :
                            new GeometryProperty<>(getGeometry(geometry, force3D)));
                } else {
                    target.setLibraryObject(implicitGeometry.getLibraryObject()
                            .map(ExternalFile::getFileLocation)
                            .orElse(null));
                }
            } else {
                source.getReference()
                        .map(Reference::getTarget)
                        .ifPresent(reference -> target.setRelativeGeometry(new GeometryProperty<>("#" + reference)));
            }

            return target;
        }

        return null;
    }

    private PointProperty getPointProperty(org.citydb.model.geometry.Point source, int dimension) {
        return helper.lookupAndPut(source) ?
                new PointProperty("#" + source.getOrCreateObjectId()) :
                new PointProperty(getPoint(source, dimension));
    }

    private CurveProperty getCurveProperty(org.citydb.model.geometry.LineString source, int dimension) {
        return helper.lookupAndPut(source) ?
                new CurveProperty("#" + source.getOrCreateObjectId()) :
                new CurveProperty(getLineString(source, dimension));
    }

    private SurfaceProperty getSurfaceProperty(org.citydb.model.geometry.Polygon source, int dimension) {
        SurfaceProperty property = helper.lookupAndPut(source) ?
                new SurfaceProperty("#" + source.getOrCreateObjectId()) :
                new SurfaceProperty(getPolygon(source, dimension));

        if (source.isReversed()) {
            OrientableSurface surface = new OrientableSurface(property);
            surface.setOrientation(Sign.MINUS);
            property = new SurfaceProperty(surface);
        }

        return property;
    }

    private SolidProperty getSolidProperty(org.citydb.model.geometry.Solid solid) {
        return helper.lookupAndPut(solid) ?
                new SolidProperty("#" + solid.getOrCreateObjectId()) :
                new SolidProperty(getSolid(solid));
    }

    private Shell getShell(org.citydb.model.geometry.CompositeSurface source) {
        Shell target = new Shell();
        source.getObjectId().ifPresent(target::setId);
        for (org.citydb.model.geometry.Polygon polygon : source.getPolygons()) {
            target.getSurfaceMembers().add(getSurfaceProperty(polygon, 3));
        }

        return target;
    }

    private Triangle getTriangle(org.citydb.model.geometry.Polygon source, int dimension) {
        List<Coordinate> points = source.getExteriorRing().getPoints().size() > 4 ?
                source.getExteriorRing().getPoints().subList(0, 4) :
                source.getExteriorRing().getPoints();

        LinearRing ring = getLinearRing(points, dimension);
        source.getExteriorRing().getObjectId().ifPresent(ring::setId);
        return new Triangle(new AbstractRingProperty(ring));
    }

    private LinearRing getLinearRing(org.citydb.model.geometry.LinearRing source, int dimension) {
        LinearRing target = new LinearRing(getPositionList(source.getPoints(), dimension));
        source.getObjectId().ifPresent(target::setId);
        return target;
    }

    private LinearRing getLinearRing(List<Coordinate> source, int dimension) {
        return new LinearRing(getPositionList(source, dimension));
    }

    private DirectPositionList getPositionList(List<Coordinate> source, int dimension) {
        DirectPositionList target = new DirectPositionList();
        List<Double> value = target.getValue();
        if (dimension == 2) {
            source.forEach(coordinate -> {
                value.add(coordinate.getX());
                value.add(coordinate.getY());
            });
        } else {
            source.forEach(coordinate -> {
                value.add(coordinate.getX());
                value.add(coordinate.getY());
                value.add(coordinate.getZ());
            });
        }

        target.setSrsDimension(dimension);
        return target;
    }

    private DirectPosition getPosition(Coordinate source, int dimension) {
        DirectPosition target = new DirectPosition();
        List<Double> value = target.getValue();
        if (dimension == 2) {
            value.add(source.getX());
            value.add(source.getY());
        } else {
            value.add(source.getX());
            value.add(source.getY());
            value.add(source.getZ());
        }

        target.setSrsDimension(dimension);
        return target;
    }

    private int getDimension(Geometry<?> geometry, boolean force3D) {
        return force3D ? 3 : geometry.getVertexDimension();
    }
}
