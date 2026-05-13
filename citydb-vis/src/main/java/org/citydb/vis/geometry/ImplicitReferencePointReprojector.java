/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.geometry;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.GeometryAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.common.Matrix4x4;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Places each implicit-geometry instance's anchor in EPSG:4326 ahead of mesh
 * transformation by {@link ImplicitInstanceTransformer}. Establishes the
 * precondition that transformer relies on: the reference point is in 4326 and
 * the transformation matrix's translation column is zero, so the remaining
 * {@code M_3x3 · v} prototype-scale offset can be converted to degree offsets
 * at the anchor's latitude.
 *
 * <p>Per implicit-geometry instance:
 * <ol>
 *   <li>Compute {@code anchor_source = referencePoint + M[*][3]} — both
 *       are in the database's source SRID (typically UTM meters).
 *       The schema's hierarchy SQL skips {@code ST_Transform} on
 *       {@code val_implicitgeom_refpoint}, and the matrix is stored as
 *       a raw float array, so neither has been reprojected yet.</li>
 *   <li>Reproject {@code anchor_source} to EPSG:4326 via
 *       {@link GeometryAdapter#transform}.</li>
 *   <li>Write the reprojected anchor back as the new referencePoint and
 *       zero out the matrix's translation column.</li>
 * </ol>
 *
 * <p><b>Why fold the translation into the anchor instead of reprojecting
 * just the referencePoint:</b> source-CRS grid axes (UTM east/north)
 * differ from geographic ENU by the meridian-convergence angle —
 * up to several degrees in datasets far from the projection's central
 * meridian. Reprojecting just the referencePoint would leave
 * {@code M_translation} on grid axes while we treat it as ENU,
 * shifting every instance by a few meters per ~100 m of translation.
 * Reprojecting {@code anchor = ref + M_translation} as one point
 * absorbs the grid rotation exactly, leaving only the prototype-scale
 * offset {@code M_3x3 · v} (typically a few meters) to be approximated
 * — at that scale the convergence error is sub-centimeter.
 *
 * <p>Each anchor is transformed via a separate SQL round-trip; for features
 * with hundreds of instances this means hundreds of round-trips. Acceptable
 * for typical city-scale data; revisit with batched MultiPoint transform if
 * profiling shows hot path.
 */
public final class ImplicitReferencePointReprojector {
    private static final Logger logger = LoggerFactory.getLogger(ImplicitReferencePointReprojector.class);
    private static final int TARGET_SRID = 4326;

    private ImplicitReferencePointReprojector() {
    }

    public static void reproject(Feature feature, DatabaseAdapter adapter) {
        int sourceSRID = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
        if (sourceSRID == TARGET_SRID) {
            return;
        }
        // Collect implicit geometries across the whole feature subtree.
        // Mode must match what VisWriter.write uses (INCLUDE_CONTAINED_FEATURES)
        // — otherwise we'd reproject anchors VisWriter doesn't process, or
        // (worse) leave anchors VisWriter does process unprojected.
        GeometryInfo geometryInfo = feature.getGeometryInfo(
                GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        if (!geometryInfo.hasImplicitGeometries()) {
            return;
        }
        GeometryAdapter geometryAdapter = adapter.getGeometryAdapter();
        for (ImplicitGeometryProperty property : geometryInfo.getImplicitGeometries()) {
            Point ref = property.getReferencePoint().orElse(null);
            Matrix4x4 transformationMatrix = property.getTransformationMatrix().orElse(null);
            if (ref == null || transformationMatrix == null) {
                continue;
            }
            // Combine refPoint + M_translation into a single source-CRS anchor,
            // then reproject the anchor to 4326. This is what folds out the
            // UTM grid-vs-ENU rotation; see the class javadoc.
            Coordinate refCoord = ref.getCoordinate();
            double anchorX = refCoord.getX() + transformationMatrix.get(0, 3);
            double anchorY = refCoord.getY() + transformationMatrix.get(1, 3);
            double anchorZ = (refCoord.getDimension() == 3 ? refCoord.getZ() : 0.0)
                    + transformationMatrix.get(2, 3);
            // Force SRID to the database's source SRS regardless of any
            // SRID stamp the JDBC driver attached to the value. The
            // val_implicitgeom_refpoint column is declared with SRID 4326
            // in the schema, so PostGIS labels every retrieved point as
            // 4326 even though the feature-hierarchy SQL skips the transform
            // and the actual coordinate values stay in source CRS. Without
            // this override, GeometryAdapter.transform short-circuits as
            // a no-op (sourceSRID == targetSRID).
            Point anchorSource = Point.of(Coordinate.of(anchorX, anchorY, anchorZ))
                    .setSRID(sourceSRID);
            try {
                Point anchor4326 = geometryAdapter.transform(anchorSource, TARGET_SRID);
                // Replace the anchor and zero the matrix translation. The
                // downstream transformer now applies M_3x3 to prototype
                // vertices and converts the (small) result to degrees at
                // the 4326 anchor latitude — convergence-angle error on a
                // few-meter offset is negligible.
                property.setReferencePoint(anchor4326);
                Matrix4x4 cleared = Matrix4x4.of(transformationMatrix.copy()
                        .set(0, 3, 0)
                        .set(1, 3, 0)
                        .set(2, 3, 0));
                property.setTransformationMatrix(cleared);
            } catch (GeometryException | SQLException e) {
                logger.warn("Failed to reproject implicit reference point on feature {}: {}",
                        feature.getObjectId().orElse("?"), e.getMessage());
            }
        }
    }
}
