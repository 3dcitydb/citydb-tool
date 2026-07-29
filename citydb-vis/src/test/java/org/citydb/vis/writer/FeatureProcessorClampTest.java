/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.VisExportException;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.options.ClampMode;
import org.citydb.vis.options.Tiles3DFormatOptions;
import org.citydb.vis.geometry.RingAttributes;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.TrsDecomposition;
import org.citydb.vis.store.InstanceStore;
import org.citydb.vis.store.SpatialEntry;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.terrain.TerrainElevationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins what {@code --clamp-to-ground} actually does to the exported geometry.
 * {@code ClampModeTest} covers the CLI/JSON token and
 * {@code VisWriterRejectionTest} the missing-ion-token rejection, but neither
 * reaches the shift itself — and a clamp that silently does nothing (or moves
 * the wrong axis, or samples terrain at the wrong point) produces an export
 * that loads fine and simply floats above, or sinks into, the ground.
 * <p>
 * Both clamped paths are covered: baked features go through
 * {@link TriangleMesh#clampToGround} (pinned per-vertex in
 * {@code TriangleMeshTest}), while GPU-instanced implicit geometries never
 * materialize a mesh and instead have the shift folded into their anchor
 * height by {@link FeatureProcessor#processInstance}.
 * <p>
 * The terrain provider is a stub: {@link TerrainElevationProvider} is an
 * interface precisely so the writer does not depend on the live Cesium ion
 * service, and the sampled height is the only thing the clamp consumes.
 */
class FeatureProcessorClampTest {

    private static final Name BUILDING = Name.of("Building", Namespaces.BUILDING);
    private static final Name TREE = Name.of("SolitaryVegetationObject", Namespaces.VEGETATION);
    private static final double EPS = 1e-9;

    // Feature footprint in EPSG:4326 degrees, floor at 100 m and roof at 112 m.
    private static final double MIN_LON = 9.1700, MAX_LON = 9.1701;
    private static final double MIN_LAT = 48.7800, MAX_LAT = 48.7801;
    private static final double FLOOR_Z = 100.0, ROOF_Z = 112.0;

    /**
     * A DB envelope that deliberately disagrees with the mesh: wider on the
     * west side and taller on both ends, as happens when several LODs or
     * non-surface geometries contribute to the feature's stored envelope. Its
     * XY centre therefore differs from the mesh's, which is what makes the
     * "which box was used" assertions below discriminating.
     */
    private static Envelope wideEnvelope() {
        return Envelope.of(
                Coordinate.of(MIN_LON - 0.0002, MIN_LAT, 90.0),
                Coordinate.of(MAX_LON, MAX_LAT, 120.0));
    }

    /** Recording stub: answers with a fixed height and remembers where it was asked. */
    private static final class StubTerrain implements TerrainElevationProvider {
        private final double height;
        private double[] sampledAt;

        StubTerrain(double height) {
            this.height = height;
        }

        @Override
        public double sampleHeight(double lon, double lat) {
            sampledAt = new double[]{lon, lat};
            return height;
        }

        @Override
        public void close() {
        }
    }

    // ---- baked features -----------------------------------------------------

    @Test
    void ellipsoidClampDropsTheMeshOntoZeroAndKeepsItsShape(@TempDir Path tempDir)
            throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            TriangleMesh mesh = processBuilding(stores, ClampMode.ELLIPSOID, null,
                    wideEnvelope());

            double[] bbox = mesh.computeBoundingBox();
            assertEquals(0.0, bbox[2], EPS, "the lowest vertex must sit on the ellipsoid");
            assertEquals(ROOF_Z - FLOOR_Z, bbox[5], EPS, "the 12 m building height must survive");
            // A clamp moves Z only — the footprint must not drift.
            assertEquals(MIN_LON, bbox[0], EPS);
            assertEquals(MAX_LAT, bbox[4], EPS);
        }
    }

    @Test
    void withoutClampingTheMeshKeepsItsAuthoredHeights(@TempDir Path tempDir) throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            TriangleMesh mesh = processBuilding(stores, null, null, wideEnvelope());

            double[] bbox = mesh.computeBoundingBox();
            assertEquals(FLOOR_Z, bbox[2], EPS);
            assertEquals(ROOF_Z, bbox[5], EPS);
        }
    }

    @Test
    void terrainClampUsesTheHeightSampledAtTheEnvelopeCentre(@TempDir Path tempDir)
            throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            StubTerrain terrain = new StubTerrain(250.0);
            Envelope envelope = wideEnvelope();

            TriangleMesh mesh = processBuilding(stores, ClampMode.CESIUM_WORLD_TERRAIN,
                    terrain, envelope);

            double[] bbox = mesh.computeBoundingBox();
            assertEquals(250.0, bbox[2], EPS, "the mesh must be lifted onto the sampled terrain");
            assertEquals(250.0 + ROOF_Z - FLOOR_Z, bbox[5], EPS);

            // Sampling location: the envelope centre (the cheap path — no mesh
            // box needed). Sampling at a corner instead would pick a different
            // terrain tile cell on steep ground.
            assertNotNull(terrain.sampledAt, "terrain was never sampled");
            assertEquals(envelope.getCenter().getX(), terrain.sampledAt[0], EPS, "sample lon");
            assertEquals(envelope.getCenter().getY(), terrain.sampledAt[1], EPS, "sample lat");
        }
    }

    @Test
    void withoutAnEnvelopeTerrainIsSampledAtTheMeshCentre(@TempDir Path tempDir) throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            StubTerrain terrain = new StubTerrain(180.0);

            processBuilding(stores, ClampMode.CESIUM_WORLD_TERRAIN, terrain, null);

            assertNotNull(terrain.sampledAt, "terrain was never sampled");
            assertEquals((MIN_LON + MAX_LON) / 2, terrain.sampledAt[0], EPS, "sample lon");
            assertEquals((MIN_LAT + MAX_LAT) / 2, terrain.sampledAt[1], EPS, "sample lat");
        }
    }

    @Test
    void unsampleableTerrainFallsBackToTheEllipsoidInsteadOfMovingTheMeshToNaN(
            @TempDir Path tempDir) throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // Point outside coverage / tile fetch failure: the provider contract
            // is to answer NaN, and propagating it would turn every vertex of
            // the feature into NaN and silently drop it from the viewer.
            TriangleMesh mesh = processBuilding(stores, ClampMode.CESIUM_WORLD_TERRAIN,
                    new StubTerrain(Double.NaN), wideEnvelope());

            assertEquals(0.0, mesh.computeBoundingBox()[2], EPS);
        }
    }

    @Test
    void terrainModeWithoutAProviderClampsToTheEllipsoid(@TempDir Path tempDir) throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // Defensive: the writer refuses this combination at construction
            // time (VisWriterRejectionTest), so if it is ever reached the mesh
            // must still land on the ellipsoid rather than skip clamping.
            TriangleMesh mesh = processBuilding(stores, ClampMode.CESIUM_WORLD_TERRAIN, null,
                    wideEnvelope());

            assertEquals(0.0, mesh.computeBoundingBox()[2], EPS);
        }
    }

    // ---- the spatial entry the clamp invalidates ----------------------------

    @Test
    void clampingDiscardsTheAuthoredEnvelopeInFavourOfTheShiftedMeshBox(@TempDir Path tempDir)
            throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            processBuilding(stores, ClampMode.ELLIPSOID, null, wideEnvelope());

            // The envelope still describes the pre-clamp feature, so reusing it
            // would key partitioning and every LOD decision off heights the
            // exported geometry no longer has.
            SpatialEntry entry = onlyEntry(stores);
            assertEquals(0.0, entry.bbox()[2], EPS, "bbox must follow the clamped mesh");
            assertEquals(ROOF_Z - FLOOR_Z, entry.bbox()[5], EPS);
            assertEquals(MIN_LON, entry.bbox()[0], EPS, "XY must come from the mesh too");
            assertEquals((MIN_LON + MAX_LON) / 2, entry.centerX(), EPS);
        }
    }

    @Test
    void withoutClampingTheAuthoredEnvelopeIsUsedAsTheCheapProxy(@TempDir Path tempDir)
            throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            Envelope envelope = wideEnvelope();
            processBuilding(stores, null, null, envelope);

            SpatialEntry entry = onlyEntry(stores);
            assertEquals(envelope.getLowerCorner().getX(), entry.bbox()[0], EPS);
            assertEquals(90.0, entry.bbox()[2], EPS, "the envelope's Z range, not the mesh's");
            assertEquals(120.0, entry.bbox()[5], EPS);
            assertEquals(envelope.getCenter().getX(), entry.centerX(), EPS);
        }
    }

    // ---- GPU-instanced implicit geometry ------------------------------------

    @Test
    void instanceClampFoldsTheShiftIntoTheAnchorHeight(@TempDir Path tempDir) throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // Template spanning local Z [-1, 3] placed 100 m up: after clamping
            // to the ellipsoid the anchor must sit at 0 - (-1) = 1 m, so the
            // template's own lowest point lands on the ground. Leaving the
            // anchor at 100 would float the tree; using the template origin
            // instead of its minimum would sink it by 1 m.
            TriangleMesh template = new TriangleMesh();
            int v0 = template.addVertex(0, 0, -1, 0, 0, 1);
            int v1 = template.addVertex(4, 0, 3, 0, 0, 1);
            int v2 = template.addVertex(2, 3, 0, 0, 0, 1);
            template.addTriangle(v0, v1, v2, 0L, -1, false, TREE);

            processInstance(stores, ClampMode.ELLIPSOID, null, template, yaw(30));

            assertEquals(1.0, stores.getInstanceStore().load(0L).anchorZ(), EPS);
        }
    }

    @Test
    void pitchedInstanceClampScansVerticesRatherThanTheRotatedAabbCorner(@TempDir Path tempDir)
            throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            // Template whose lowest point under a 45° pitch is NOT an AABB
            // corner: local AABB is x[0,4] y[-3,0] z[0,2], and the transform's
            // Z row is (0, √½, √½).
            //   true vertex minimum: √½·(-3 + 2) = -0.7071 -> anchor +0.7071
            //   rotated AABB corner: √½·(-3 + 0) = -2.1213 -> anchor +2.1213
            // The AABB corner lies 1.4 m below any real vertex, which is what
            // would leave every pitched instance hovering.
            TriangleMesh template = new TriangleMesh();
            int v0 = template.addVertex(0, 0, 0, 0, 0, 1);
            int v1 = template.addVertex(4, 0, 0, 0, 0, 1);
            int v2 = template.addVertex(2, -3, 2, 0, 0, 1);
            template.addTriangle(v0, v1, v2, 0L, -1, false, TREE);

            processInstance(stores, ClampMode.ELLIPSOID, null, template, pitch45AboutX());

            assertEquals(1 / Math.sqrt(2), stores.getInstanceStore().load(0L).anchorZ(), 1e-9);
        }
    }

    @Test
    void instanceTerrainClampSamplesAtTheAnchorAndFallsBackOnNaN(@TempDir Path tempDir)
            throws Exception {
        TriangleMesh template = new TriangleMesh();
        int v0 = template.addVertex(0, 0, 0, 0, 0, 1);
        int v1 = template.addVertex(2, 0, 0, 0, 0, 1);
        int v2 = template.addVertex(0, 2, 4, 0, 0, 1);
        template.addTriangle(v0, v1, v2, 0L, -1, false, TREE);

        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("terrain"))) {
            StubTerrain terrain = new StubTerrain(310.0);
            processInstance(stores, ClampMode.CESIUM_WORLD_TERRAIN, terrain, template, yaw(0));

            // Template minimum is 0, so the anchor becomes the sampled height.
            assertEquals(310.0, stores.getInstanceStore().load(0L).anchorZ(), EPS);
            assertNotNull(terrain.sampledAt, "terrain was never sampled");
            assertEquals(MIN_LON, terrain.sampledAt[0], EPS, "instances sample at their anchor");
            assertEquals(MIN_LAT, terrain.sampledAt[1], EPS);
        }

        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("nan"))) {
            processInstance(stores, ClampMode.CESIUM_WORLD_TERRAIN, new StubTerrain(Double.NaN),
                    template, yaw(0));
            assertEquals(0.0, stores.getInstanceStore().load(0L).anchorZ(), EPS);
        }
    }

    @Test
    void withoutClampingTheInstanceKeepsItsAuthoredAnchorHeight(@TempDir Path tempDir)
            throws Exception {
        try (VisExportStores stores = new VisExportStores(2, tempDir.resolve("stores"))) {
            TriangleMesh template = new TriangleMesh();
            int v0 = template.addVertex(0, 0, -1, 0, 0, 1);
            int v1 = template.addVertex(4, 0, 3, 0, 0, 1);
            int v2 = template.addVertex(2, 3, 0, 0, 0, 1);
            template.addTriangle(v0, v1, v2, 0L, -1, false, TREE);

            processInstance(stores, null, null, template, yaw(30));

            assertEquals(ANCHOR_Z, stores.getInstanceStore().load(0L).anchorZ(), EPS);
        }
    }

    // ---- fixture ------------------------------------------------------------

    private static final double ANCHOR_Z = 100.0;

    private static FeatureProcessor processor(VisExportStores stores, ClampMode clampMode,
                                              TerrainElevationProvider terrain) {
        Tiles3DFormatOptions options = new Tiles3DFormatOptions();
        options.setClampMode(clampMode);
        return new FeatureProcessor(stores, options, new AttributeEncoder(), terrain, () -> null);
    }

    /**
     * Run one two-quad building (floor + roof) through {@code process} and
     * return the mesh as it was persisted.
     */
    private static TriangleMesh processBuilding(VisExportStores stores, ClampMode clampMode,
                                                TerrainElevationProvider terrain,
                                                Envelope envelope)
            throws VisExportException, IOException {
        List<GeometryProperty> geometry = List.of(
                quad(FLOOR_Z), quad(ROOF_Z));

        processor(stores, clampMode, terrain).process(1L, "BLDG_1", "Building BLDG_1",
                "Building", Namespaces.BUILDING, envelope, Map.of(), geometry,
                new RingAttributes(null, null, null));

        SpatialEntry entry = onlyEntry(stores);
        return stores.getMeshStore().load(entry.meshHandle());
    }

    /** Horizontal quad over the fixture footprint at the given height. */
    private static GeometryProperty quad(double z) {
        Polygon polygon = Polygon.of(LinearRing.of(List.of(
                Coordinate.of(MIN_LON, MIN_LAT, z),
                Coordinate.of(MAX_LON, MIN_LAT, z),
                Coordinate.of(MAX_LON, MAX_LAT, z),
                Coordinate.of(MIN_LON, MAX_LAT, z),
                Coordinate.of(MIN_LON, MIN_LAT, z))));
        return GeometryProperty.of(Name.of("lod2MultiSurface", Namespaces.CORE), polygon);
    }

    /** Place one instance of {@code template} at the fixture anchor. */
    private static void processInstance(VisExportStores stores, ClampMode clampMode,
                                        TerrainElevationProvider terrain,
                                        TriangleMesh template, Matrix4x4 matrix)
            throws VisExportException {
        PrototypeRegistry.Prototype prototype =
                new PrototypeRegistry.Prototype(0, template, template.computeBoundingBox());
        TrsDecomposition.Result trs = TrsDecomposition.decompose(matrix);
        assertNotNull(trs, "the fixture matrix must be decomposable into rotation x scale");

        processor(stores, clampMode, terrain).processInstance(1L, "tree_1", 0L, prototype,
                matrix, trs, Point.of(Coordinate.of(MIN_LON, MIN_LAT, ANCHOR_Z)),
                new float[]{1f, 1f, 1f, 1f});
    }

    /** Rotation about the ENU up axis — the upright case, Z row (0, 0, 1). */
    private static Matrix4x4 yaw(double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        return Matrix4x4.ofRowMajor(
                c, -s, 0, 0,
                s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);
    }

    /** 45° rotation about the ENU east axis — Z row (0, √½, √½). */
    private static Matrix4x4 pitch45AboutX() {
        double c = Math.cos(Math.toRadians(45)), s = Math.sin(Math.toRadians(45));
        return Matrix4x4.ofRowMajor(
                1, 0, 0, 0,
                0, c, -s, 0,
                0, s, c, 0,
                0, 0, 0, 1);
    }

    private static SpatialEntry onlyEntry(VisExportStores stores) {
        var iterator = stores.getSpatialEntryStore().iterator();
        assertNotNull(iterator);
        SpatialEntry entry = iterator.hasNext() ? iterator.next() : null;
        assertNotNull(entry, "the processor persisted no spatial entry");
        assertNull(iterator.hasNext() ? iterator.next() : null, "expected exactly one entry");
        return entry;
    }
}
