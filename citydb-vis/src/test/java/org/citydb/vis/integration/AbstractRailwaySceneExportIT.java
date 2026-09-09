/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.integration;

import org.citydb.config.common.SrsReference;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.database.DatabaseManager;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.connection.ConnectionDetails;
import org.citydb.io.IOAdapter;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.util.GeometryInfo;
import org.citydb.operation.exporter.ExportOptions;
import org.citydb.operation.exporter.Exporter;
import org.citydb.operation.exporter.options.ImplicitGeometryScope;
import org.citydb.query.Query;
import org.citydb.query.executor.QueryExecutor;
import org.citydb.query.executor.QueryResult;
import org.citydb.vis.geometry.ImplicitReferencePointReprojector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared harness for the live-DB end-to-end export smoke tests. Drives the real
 * pipeline {@code DB -> Exporter.exportFeature -> IOAdapter writer.write},
 * mirroring {@code VisExportController.doExport} minus picocli, and leaves the
 * format-specific output assertions to subclasses (one per writer).
 * <p>
 * <b>Requires a populated test database.</b> The password must be supplied via
 * the {@code CITYDB_TEST_PASSWORD} environment variable — no credential is
 * committed to the repo. The remaining parameters default to the local
 * development instance and can be overridden via their own env vars
 * ({@code CITYDB_TEST_HOST/PORT/DATABASE/USER/SCHEMA}). Environment variables are
 * inherited by the forked test JVM, so no Gradle wiring is needed.
 * <p>
 * <b>A missing or unreachable database fails this class; it does not skip it.</b>
 * The class is reachable only through the {@code integrationTest} Gradle task,
 * which CI never invokes, so the only way to get here is to ask for it. Skipping
 * at that point would report green for the very writer pipeline the run was
 * meant to exercise, and would make a typo'd password indistinguishable from a
 * healthy run. Use {@code gradlew test} for the database-free unit suite.
 * <p>
 * The full textured railway export loads every source texture and builds atlases
 * concurrently, so the forked test JVM is given a larger heap via
 * {@code maxHeapSize} in {@code citydb-vis/build.gradle} (the default crashes the
 * worker mid-export).
 */
abstract class AbstractRailwaySceneExportIT {
    /** Output-file base name; both writers strip the extension to a {@code railway} scene dir. */
    static final String SCENE_NAME = "railway";

    /** The railway scene carries 15 implicit-geometry instances of 3 shared prototypes. */
    static final int EXPECTED_IMPLICIT_INSTANCES = 15;

    private static final String SETUP_HINT =
            "Start the local test citydb and set CITYDB_TEST_PASSWORD (optionally " +
                    "CITYDB_TEST_HOST/PORT/DATABASE/USER/SCHEMA), or run 'gradlew test' " +
                    "for the database-free unit suite.";

    static DatabaseManager databaseManager;
    static DatabaseAdapter adapter;

    @BeforeAll
    static void connect() {
        // The password is intentionally NOT defaulted — it must be supplied via
        // the CITYDB_TEST_PASSWORD env var (no secret is committed to the repo).
        String password = System.getenv("CITYDB_TEST_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "CITYDB_TEST_PASSWORD is not set, so the integration test has no database " +
                            "to run against. " + SETUP_HINT);
        }

        String host = property("HOST", "localhost");
        String port = property("PORT", "5432");
        String database = property("DATABASE", "test_citydb_v5_railway");
        String user = property("USER", "postgres");
        String schema = property("SCHEMA", "citydb");

        ConnectionDetails connectionDetails = new ConnectionDetails()
                .setHost(host)
                .setPort(Integer.valueOf(port))
                .setDatabase(database)
                .setUser(user)
                .setPassword(password)
                .setSchema(schema);

        DatabaseManager manager = DatabaseManager.newInstance();
        try {
            manager.connect(connectionDetails);
        } catch (Throwable e) {
            // Surface the cause instead of swallowing it: a wrong password, a
            // stopped server and a missing schema all land here and must stay
            // distinguishable.
            manager.disconnect();
            throw new IllegalStateException("Failed to connect to the test database " +
                    user + "@" + host + ":" + port + "/" + database + " (schema " + schema + "). " +
                    SETUP_HINT, e);
        }

        databaseManager = manager;
        adapter = manager.getAdapter();
    }

    @AfterAll
    static void disconnect() {
        if (databaseManager != null) {
            databaseManager.disconnect();
            databaseManager = null;
            adapter = null;
        }
    }

    /**
     * Stream every queried feature through the given writer and assert all were
     * written. Returns the scene output directory ({@code <out>/railway}) for
     * format-specific structural assertions.
     *
     * @param tempDir       per-test scratch root (JUnit {@code @TempDir})
     * @param ioAdapter     the format adapter under test (I3S / 3D Tiles)
     * @param formatOptions the matching format options
     * @param fileExtension output file extension including the dot (e.g. {@code .i3s})
     */
    Path runExport(Path tempDir, IOAdapter ioAdapter, OutputFormatOptions formatOptions,
                   String fileExtension) throws Exception {
        assertNotNull(databaseManager, "connect() established no database connection.");
        assertTrue(databaseManager.isConnected(),
                "The database connection dropped before the export started.");

        // Visualization formats require WGS84 geographic coordinates; the DB
        // exporter reprojects via PostGIS as part of the SQL it builds. The
        // per-feature implicit-geometry scope mirrors VisExportController:
        // under the default GLOBAL scope, shared templates arrive as bare
        // references that the vis pipeline cannot resolve.
        ExportOptions exportOptions = new ExportOptions();
        exportOptions.setTargetSrs(SrsReference.of(4326));
        exportOptions.setImplicitGeometryScope(ImplicitGeometryScope.TOP_LEVEL_FEATURE);

        Query query = new Query();
        QueryExecutor executor = QueryExecutor.builder(adapter).build(query);
        long expected = executor.countHits();
        // An empty database is a misconfigured fixture, not a reason to opt out.
        assertTrue(expected > 0, "Test database holds no features; expected the populated " +
                "railway dataset. " + SETUP_HINT);

        // The writer wipes its whole temp directory on close
        // (VisExportStores.close -> deleteDirectoryTree). Keep it strictly
        // separate from the output tree so the written scene survives, exactly as
        // the CLI controller does (output at -o, temp in a sibling .tmp dir).
        Path outputDir = Files.createDirectories(tempDir.resolve("out"));
        Path workDir = Files.createDirectories(tempDir.resolve("work"));

        // Root the DB exporter's output inside the writer's temp dir, exactly as
        // VisExportController does. The exporter emits external texture files
        // under <workDir>/appearance/..., and TextureStore.getSourcePath resolves
        // the registered (relative) texture URIs against that same temp dir —
        // without this the textures are written nowhere and every node falls back
        // to untextured rendering.
        exportOptions.setOutputFile(new RegularOutputFile(workDir.resolve("temp")));

        WriteOptions writeOptions = new WriteOptions();
        writeOptions.setTempDirectory(workDir);
        writeOptions.setSrsName(adapter.getGeometryAdapter().getSrsHelper().getDefaultIdentifier(4326));
        writeOptions.getFormatOptions().set(formatOptions);

        OutputFile output = new RegularOutputFile(outputDir.resolve(SCENE_NAME + fileExtension));

        AtomicInteger written = new AtomicInteger();
        AtomicInteger implicitInstances = new AtomicInteger();
        Exporter exporter = Exporter.newInstance();

        ioAdapter.initialize(getClass().getClassLoader());
        try (FeatureWriter writer = ioAdapter.createWriter(output, writeOptions)) {
            exporter.startSession(adapter, exportOptions);
            try (QueryResult result = executor.executeQuery()) {
                long sequenceId = 1;
                while (result.hasNext()) {
                    long id = result.getId();
                    Feature feature = exporter.exportFeature(id, sequenceId++).join();
                    assertNotNull(feature, "Exporter returned null for feature id " + id);

                    // Fold the matrix translation and grid rotation into the
                    // anchor exactly as the CLI controller does before handing
                    // off to the writer.
                    ImplicitReferencePointReprojector.reproject(feature, adapter);
                    implicitInstances.addAndGet(countImplicitInstances(feature));

                    Boolean ok = writer.write(feature).join();
                    assertEquals(Boolean.TRUE, ok, "Writer rejected feature id " + id);
                    written.incrementAndGet();
                }
            } finally {
                exporter.closeSession();
            }
        }

        assertEquals(expected, written.get(), "Not all queried features were written.");
        assertEquals(EXPECTED_IMPLICIT_INSTANCES, implicitInstances.get(),
                "Wrong number of inline implicit-geometry instances reached the writer.");

        // Both writers strip the extension and root the scene under <out>/railway.
        return outputDir.resolve(SCENE_NAME);
    }

    /**
     * Count the implicit-geometry instances the feature carries inline and pin
     * their reprojected anchors to the scene's WGS84 neighborhood. Guards two
     * silent degradations the structural output checks cannot see: an exporter
     * scope that replaces shared prototypes with bare references (the writer
     * skips those without failing), and a reprojection regression that sends
     * anchors continents away (the writer places them without complaint).
     */
    private int countImplicitInstances(Feature feature) {
        int instances = 0;
        GeometryInfo geometryInfo = feature.getGeometryInfo(GeometryInfo.Mode.INCLUDE_CONTAINED_FEATURES);
        for (ImplicitGeometryProperty property : geometryInfo.getImplicitGeometries()) {
            assertTrue(property.getObject().isPresent(),
                    "Implicit-geometry property on feature " + feature.getObjectId().orElse("?") +
                            " carries a bare reference instead of an inline prototype.");
            Point referencePoint = property.getReferencePoint().orElse(null);
            if (referencePoint != null) {
                Coordinate coordinate = referencePoint.getCoordinate();
                assertTrue(coordinate.getX() > 12.5 && coordinate.getX() < 13.5
                                && coordinate.getY() > 52.0 && coordinate.getY() < 52.7,
                        "Reprojected implicit anchor " + coordinate.getX() + "," + coordinate.getY() +
                                " lies outside the railway scene's WGS84 neighborhood.");
            }
            instances++;
        }

        return instances;
    }

    private static String property(String key, String defaultValue) {
        String value = System.getenv("CITYDB_TEST_" + key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
