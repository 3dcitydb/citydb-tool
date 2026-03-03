package org.citydb.io.ifc.reader;

import org.bimserver.models.ifc4.IfcProduct;
import org.bimserver.models.ifc4.IfcRepresentation;
import org.citydb.model.geometry.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class GeometryProcessor {

    private static final Logger logger = LoggerFactory.getLogger(GeometryProcessor.class);

    private static final Set<String> SOLID_REPRESENTATION_TYPES = Set.of(
            "SweptSolid", "Brep", "AdvancedBrep", "CSG", "Clipping", "BoundingBox"
    );

    private final Map<String, List<double[]>> geometryCache;
    private final GeoReferencing geoRef;

    public GeometryProcessor(Map<String, List<double[]>> geometryCache, GeoReferencing geoRef) {
        this.geometryCache = geometryCache;
        this.geoRef = geoRef;
    }

    public List<double[]> getTransformedPolygons(IfcProduct element) {
        if (geometryCache == null) return Collections.emptyList();
        List<double[]> cached = geometryCache.get(element.getGlobalId());
        if (cached == null || cached.isEmpty()) return Collections.emptyList();

        List<double[]> transformed = new ArrayList<>();
        for (double[] poly : cached) {
            double[] tPoly = new double[poly.length];
            for (int i = 0; i < poly.length; i += 3) {
                double[] v = geoRef.transformVertex(new double[]{poly[i], poly[i + 1], poly[i + 2]});
                tPoly[i] = v[0];
                tPoly[i + 1] = v[1];
                tPoly[i + 2] = v[2];
            }
            transformed.add(tPoly);
        }
        return transformed;
    }

    public boolean isIntendedSolid(IfcProduct element) {
        if (element.getRepresentation() == null) {
            return false;
        }

        for (IfcRepresentation rep : element.getRepresentation().getRepresentations()) {
            String repId = rep.getRepresentationIdentifier();
            if (repId != null && !repId.toLowerCase().matches("body|mesh|facetedbrep")) {
                continue;
            }

            String repType = rep.getRepresentationType();
            if (repType != null && SOLID_REPRESENTATION_TYPES.contains(repType)) {
                return true;
            }
        }

        return false;
    }

    public Polygon createPolygon(double[] coords) {
        List<Coordinate> coordinates = new ArrayList<>();
        for (int i = 0; i < coords.length; i += 3) {
            coordinates.add(Coordinate.of(
                    Math.round(coords[i] * 1000.0) / 1000.0,
                    Math.round(coords[i + 1] * 1000.0) / 1000.0,
                    Math.round(coords[i + 2] * 1000.0) / 1000.0
            ));
        }
        LinearRing ring = LinearRing.of(coordinates);
        Polygon polygon = Polygon.of(ring);
        polygon.setObjectId("UUID_" + UUID.randomUUID());
        return polygon;
    }

    public GeometryResult buildGeometry(IfcProduct element, List<double[]> polygonCoords) {
        boolean isSolid = isIntendedSolid(element);
        List<Polygon> polygons = new ArrayList<>();

        for (double[] coords : polygonCoords) {
            polygons.add(createPolygon(coords));
        }

        Geometry<?> geometry;
        String geometryName;

        if (isSolid) {
            CompositeSurface shell = CompositeSurface.of(polygons);
            Solid solid = Solid.of(shell);
            solid.setObjectId("UUID_" + UUID.randomUUID());
            geometry = solid;
            geometryName = "lod3Solid";
        } else {
            MultiSurface multiSurface = MultiSurface.of(polygons);
            multiSurface.setObjectId("UUID_" + UUID.randomUUID());
            geometry = multiSurface;
            geometryName = "lod3MultiSurface";
        }

        return new GeometryResult(geometry, geometryName, polygons);
    }

    public record GeometryResult(Geometry<?> geometry, String geometryName, List<Polygon> polygons) {}
}
