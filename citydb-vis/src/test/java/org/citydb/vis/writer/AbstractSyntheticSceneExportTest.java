/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.io.IOAdapter;
import org.citydb.io.writer.FeatureWriter;
import org.citydb.io.writer.WriteOptions;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.property.RelationType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared harness for the <b>database-free</b> end-to-end writer smoke tests.
 * Drives the real writer pipeline {@code IOAdapter.createWriter -> write(feature)
 * -> close()} over a hand-built synthetic scene, so the whole close phase
 * (partitioning, cell leaves, mixed-node split, aggregation, prototype
 * finalization, format-specific output) runs in CI.
 * <p>
 * This is the CI-visible counterpart to
 * {@code integration.AbstractRailwaySceneExportIT}: the live-DB ITs exercise the
 * same code with real data but are excluded from {@code gradlew test} whenever
 * {@code CITYDB_TEST_PASSWORD} is absent, which left the entire
 * {@code org.citydb.vis.writer} package at zero coverage on GitHub. Only the
 * <i>features</i> come from the database in a real export — {@link FeatureWriter#write}
 * takes a plain {@link Feature} and the writers never read
 * {@link WriteOptions#getSrsName()} — so the DB can be replaced by a fixture
 * without touching production code.
 * <p>
 * The scene is deliberately small (nine features, 64x64 textures) so the suite
 * stays in the sub-second range and needs no enlarged heap, while still hitting
 * every branch the close phase keys on:
 * <ul>
 *   <li><b>multi-cell grid</b> — features spread over ~220 m at a 50 m grid edge,
 *       so partitioning yields several leaves and aggregation must wrap them;</li>
 *   <li><b>textured and untextured content in the same cell</b> — triggers the
 *       mixed-texture push-down split;</li>
 *   <li><b>shared textures</b> — two images across four buildings, pinning the
 *       {@code TextureStore} URI dedup through to the atlas;</li>
 *   <li><b>implicit geometry</b> — three instances of one shared template, which
 *       is the instanced path for 3D Tiles and the baked path for I3S;</li>
 *   <li><b>authored vs. derived bbox</b> — half the features carry an envelope,
 *       half leave it to the mesh AABB.</li>
 * </ul>
 */
abstract class AbstractSyntheticSceneExportTest {
    /** Output-file base name; both writers strip the extension to a scene dir. */
    static final String SCENE_NAME = "synthetic";

    /** Grid edge length in meters; small enough that the scene spans several cells. */
    static final double GRID_EDGE_LENGTH = 50.0;

    static final Name BUILDING = Name.of("Building", Namespaces.BUILDING);
    static final Name ROOF_SURFACE = Name.of("RoofSurface", Namespaces.CONSTRUCTION);
    static final Name VEGETATION = Name.of("SolitaryVegetationObject", Namespaces.VEGETATION);

    /** Textured buildings, untextured buildings, and implicit-geometry instances. */
    static final int TEXTURED_BUILDINGS = 4;
    static final int UNTEXTURED_BUILDINGS = 2;
    static final int TREES = 3;
    static final int FEATURE_COUNT = TEXTURED_BUILDINGS + UNTEXTURED_BUILDINGS + TREES;

    /**
     * Triangles contributed by the buildings alone: five quads each (roof plus
     * four walls), two triangles per quad. A lower bound — the T-junction pass
     * and the aggregation levels may add more, never fewer. Trees are counted
     * separately because an instanced tree encodes its template once no matter
     * how often it is placed.
     */
    static final int BUILDING_TRIANGLES = (TEXTURED_BUILDINGS + UNTEXTURED_BUILDINGS) * 5 * 2;

    /** Triangles in one tree template: a closed box of six quads. */
    static final int TREE_TEMPLATE_TRIANGLES = 6 * 2;

    /**
     * Lower bound on the number of nodes carrying their own geometry. The four
     * textured buildings stand 70 m apart at a 50 m grid
     * edge, so no two share a cell and each must become its own leaf. Derived
     * from the fixture layout on purpose: a bound of "more than one" is met by
     * the mixed-node split alone even when the grid collapses to 1x1, and would
     * not notice partitioning degenerating.
     */
    static final int MIN_MESH_NODES = TEXTURED_BUILDINGS;

    // Scene anchor (Stuttgart). The writers assume X=lon deg, Y=lat deg, Z=m.
    private static final double ANCHOR_LON = 9.17;
    private static final double ANCHOR_LAT = 48.78;
    private static final double METERS_PER_DEGREE_LAT = 111320.0;
    private static final double METERS_PER_DEGREE_LON =
            METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(ANCHOR_LAT));

    private static final Name THEME = Name.of("rgbTexture");
    private static final String TEXTURE_A = "appearance/tex_a.png";
    private static final String TEXTURE_B = "appearance/tex_b.png";

    /**
     * Build the scene, stream it through the given writer and assert every
     * feature was accepted. Returns the scene output directory for
     * format-specific structural assertions.
     *
     * @param tempDir       per-test scratch root (JUnit {@code @TempDir})
     * @param ioAdapter     the format adapter under test (I3S / 3D Tiles)
     * @param formatOptions the matching format options
     * @param fileExtension output file extension including the dot (e.g. {@code .i3s})
     */
    Path runExport(Path tempDir, IOAdapter ioAdapter, OutputFormatOptions formatOptions,
                   String fileExtension) throws Exception {
        // The writer wipes its whole temp directory on close
        // (VisExportStores.close -> deleteDirectoryTree), so the output tree must
        // stay strictly outside it — exactly as the CLI controller arranges it.
        Path outputDir = Files.createDirectories(tempDir.resolve("out"));
        Path workDir = Files.createDirectories(tempDir.resolve("work"));

        // Texture URIs are registered relative to the temp directory and
        // resolved against it by TextureStore.getSourcePath, so the image files
        // must be written there — this is what the DB texture exporter does in a
        // real export.
        writeTexture(workDir.resolve(TEXTURE_A), 0xFFCC5533);
        writeTexture(workDir.resolve(TEXTURE_B), 0xFF3366CC);

        WriteOptions writeOptions = new WriteOptions();
        writeOptions.setTempDirectory(workDir);
        // Pin the worker count: it becomes the mesh-store shard count and the
        // downstream pool parallelism, so a fixed value keeps the test cheap and
        // its store layout reproducible across machines.
        writeOptions.setNumberOfThreads(2);
        writeOptions.getFormatOptions().set(formatOptions);

        OutputFile output = new RegularOutputFile(outputDir.resolve(SCENE_NAME + fileExtension));

        List<Feature> features = buildScene();
        assertEquals(FEATURE_COUNT, features.size(), "Fixture built an unexpected feature count.");

        ioAdapter.initialize(getClass().getClassLoader());
        try (FeatureWriter writer = ioAdapter.createWriter(output, writeOptions)) {
            for (Feature feature : features) {
                Boolean ok = writer.write(feature).join();
                assertEquals(Boolean.TRUE, ok,
                        "Writer rejected feature " + feature.getObjectId().orElse("?"));
            }
        }

        // write() also returns true for features the writer silently drops (no
        // geometry), so "all writes returned true" alone proves nothing — the
        // subclasses assert the actual output tree.
        Path sceneDir = outputDir.resolve(SCENE_NAME);
        assertTrue(Files.isDirectory(sceneDir),
                "Writer produced no scene directory at " + sceneDir);
        return sceneDir;
    }

    // ---- scene fixture ------------------------------------------------------

    /**
     * Nine features laid out over ~220 x 70 m: four textured buildings sharing
     * two images, two untextured buildings carrying X3DMaterial, and three
     * vegetation features placing one shared implicit-geometry template.
     */
    private List<Feature> buildScene() {
        List<Feature> features = new ArrayList<>(FEATURE_COUNT);

        // Textured buildings, one per grid column so partitioning yields
        // several cells. The first shares cell 0 with an untextured building
        // and a tree, which is what makes that node mixed.
        double[] texturedEast = {0, 70, 140, 210};
        for (int i = 0; i < TEXTURED_BUILDINGS; i++) {
            features.add(texturedBuilding("building_tex_" + i, texturedEast[i], 0,
                    i % 2 == 0 ? TEXTURE_A : TEXTURE_B));
        }

        // Untextured buildings with an X3DMaterial: the COLOR_0 / vertex-colour
        // path, and the untextured half of the mixed-node split.
        features.add(materialBuilding("building_mat_0", 35, 60, Color.of(0.8, 0.2, 0.2)));
        features.add(materialBuilding("building_mat_1", 105, 60, Color.of(0.2, 0.8, 0.4)));

        // One template, three placements: the prototype registry must register
        // it once and the three instances differ only by their transform.
        ImplicitGeometry template = treeTemplate();
        double[][] treePlacement = {{20, 20, 1.0, 0}, {90, 20, 1.5, 30}, {160, 20, 2.0, 75}};
        for (int i = 0; i < TREES; i++) {
            double[] p = treePlacement[i];
            features.add(tree("tree_" + i, template, p[0], p[1], p[2], p[3]));
        }

        return features;
    }

    private Feature texturedBuilding(String objectId, double east, double north, String textureUri) {
        List<Polygon> shell = box(east, north, 12, 10, 9, objectId);
        Feature feature = building(objectId, shell);

        ParameterizedTexture texture = ParameterizedTexture.newInstance();
        texture.setTextureImage(ExternalFile.of(textureUri));
        for (Polygon polygon : shell) {
            texture.addTextureCoordinates(polygon.getExteriorRing(), quadUVs());
        }
        feature.addAppearance(AppearanceProperty.of(THEME,
                Appearance.newInstance().addSurfaceData(SurfaceDataProperty.of(texture))));

        // Authored envelope: FeatureProcessor prefers it over the mesh AABB for
        // the partitioning bbox. The material buildings below leave it unset so
        // the derived-bbox branch is exercised too.
        feature.setEnvelope(Envelope.of(
                Coordinate.of(lon(east), lat(north), 0),
                Coordinate.of(lon(east + 12), lat(north + 10), 9)));
        return feature;
    }

    private Feature materialBuilding(String objectId, double east, double north, Color diffuse) {
        List<Polygon> shell = box(east, north, 12, 10, 9, objectId);
        Feature feature = building(objectId, shell);

        X3DMaterial material = X3DMaterial.newInstance().setDiffuseColor(diffuse);
        for (Polygon polygon : shell) {
            material.addTarget(polygon);
        }
        feature.addAppearance(AppearanceProperty.of(THEME,
                Appearance.newInstance().addSurfaceData(SurfaceDataProperty.of(material))));
        return feature;
    }

    /**
     * A building whose roof polygon is owned by a contained RoofSurface, so the
     * writer's INCLUDE_CONTAINED_FEATURES walk and the per-surface owner-depth
     * sort both run (that is where per-feature-type styling gets its type from).
     */
    private Feature building(String objectId, List<Polygon> shell) {
        Feature feature = Feature.of(BUILDING).setObjectId(objectId);
        feature.addGeometry(GeometryProperty.of(Name.of("lod2Solid", Namespaces.CORE),
                MultiSurface.of(shell.subList(1, shell.size()))));

        Feature roof = Feature.of(ROOF_SURFACE).setObjectId(objectId + "_roof");
        roof.addGeometry(GeometryProperty.of(Name.of("lod2MultiSurface", Namespaces.CORE),
                MultiSurface.of(List.of(shell.get(0)))));
        feature.addFeature(FeatureProperty.of(Name.of("boundedBy", Namespaces.CORE),
                roof, RelationType.CONTAINS));
        return feature;
    }

    /** A 2 m box in template-local meters, centred on the template origin. */
    private ImplicitGeometry treeTemplate() {
        List<Polygon> shell = new ArrayList<>();
        double h = 1.0;
        double[][][] quads = {
                {{-h, -h, 2 * h}, {h, -h, 2 * h}, {h, h, 2 * h}, {-h, h, 2 * h}},   // top
                {{-h, -h, 0}, {-h, h, 0}, {h, h, 0}, {h, -h, 0}},                   // bottom
                {{-h, -h, 0}, {h, -h, 0}, {h, -h, 2 * h}, {-h, -h, 2 * h}},         // south
                {{h, -h, 0}, {h, h, 0}, {h, h, 2 * h}, {h, -h, 2 * h}},             // east
                {{h, h, 0}, {-h, h, 0}, {-h, h, 2 * h}, {h, h, 2 * h}},             // north
                {{-h, h, 0}, {-h, -h, 0}, {-h, -h, 2 * h}, {-h, h, 2 * h}}          // west
        };
        for (int i = 0; i < quads.length; i++) {
            shell.add(closedQuad(quads[i], "tree_template_" + i));
        }

        ImplicitGeometry template = ImplicitGeometry.of(MultiSurface.of(shell))
                .setObjectId("tree_template");

        X3DMaterial material = X3DMaterial.newInstance().setDiffuseColor(Color.of(0.1, 0.6, 0.2));
        for (Polygon polygon : shell) {
            material.addTarget(polygon);
        }
        template.setAppearances(List.of(AppearanceProperty.of(THEME,
                Appearance.newInstance().addSurfaceData(SurfaceDataProperty.of(material)))));
        return template;
    }

    private Feature tree(String objectId, ImplicitGeometry template,
                         double east, double north, double scale, double headingDegrees) {
        double c = Math.cos(Math.toRadians(headingDegrees)) * scale;
        double s = Math.sin(Math.toRadians(headingDegrees)) * scale;
        // Rotation about Z composed with a uniform scale: decomposable into
        // rotation x scale, so the instanced path accepts it (no shear).
        Matrix4x4 transform = Matrix4x4.ofRowMajor(
                c, -s, 0, 0,
                s, c, 0, 0,
                0, 0, scale, 0,
                0, 0, 0, 1);

        return Feature.of(VEGETATION).setObjectId(objectId)
                .addImplicitGeometry(ImplicitGeometryProperty
                        .of(Name.of("lod2ImplicitRepresentation", Namespaces.CORE), template)
                        .setTransformationMatrix(transform)
                        .setReferencePoint(Point.of(Coordinate.of(lon(east), lat(north), 0))));
    }

    // ---- geometry helpers ---------------------------------------------------

    /** Roof first, then the four walls, wound counter-clockwise seen from outside. */
    private List<Polygon> box(double east, double north, double width, double depth,
                              double height, String objectId) {
        double x0 = lon(east), x1 = lon(east + width);
        double y0 = lat(north), y1 = lat(north + depth);
        double[][][] quads = {
                {{x0, y0, height}, {x1, y0, height}, {x1, y1, height}, {x0, y1, height}},
                {{x0, y0, 0}, {x1, y0, 0}, {x1, y0, height}, {x0, y0, height}},
                {{x1, y0, 0}, {x1, y1, 0}, {x1, y1, height}, {x1, y0, height}},
                {{x1, y1, 0}, {x0, y1, 0}, {x0, y1, height}, {x1, y1, height}},
                {{x0, y1, 0}, {x0, y0, 0}, {x0, y0, height}, {x0, y1, height}}
        };

        List<Polygon> shell = new ArrayList<>(quads.length);
        for (int i = 0; i < quads.length; i++) {
            shell.add(closedQuad(quads[i], objectId + "_p" + i));
        }
        return shell;
    }

    private Polygon closedQuad(double[][] corners, String objectId) {
        List<Coordinate> ring = new ArrayList<>(corners.length + 1);
        for (double[] corner : corners) {
            ring.add(Coordinate.of(corner[0], corner[1], corner[2]));
        }
        ring.add(ring.get(0));
        return Polygon.of(LinearRing.of(ring)).setObjectId(objectId);
    }

    /** UVs for a closed quad ring (five coordinates, first repeated last). */
    private static List<TextureCoordinate> quadUVs() {
        return List.of(
                TextureCoordinate.of(0f, 0f), TextureCoordinate.of(1f, 0f),
                TextureCoordinate.of(1f, 1f), TextureCoordinate.of(0f, 1f),
                TextureCoordinate.of(0f, 0f));
    }

    private static double lon(double eastMeters) {
        return ANCHOR_LON + eastMeters / METERS_PER_DEGREE_LON;
    }

    private static double lat(double northMeters) {
        return ANCHOR_LAT + northMeters / METERS_PER_DEGREE_LAT;
    }

    /** A 64x64 single-colour PNG — enough to exercise the whole atlas chain. */
    private static void writeTexture(Path path, int argb) throws IOException {
        Files.createDirectories(path.getParent());
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                // A visible checker so a mirrored or transposed atlas blit is not
                // hidden behind a flat fill.
                image.setRGB(x, y, ((x / 8 + y / 8) % 2 == 0) ? argb : 0xFF000000);
            }
        }
        assertTrue(ImageIO.write(image, "png", path.toFile()),
                "No PNG writer available for the fixture texture.");
    }
}
