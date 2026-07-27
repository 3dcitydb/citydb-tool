/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Matrix2x2;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.vis.store.TextureStore;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link AppearanceExtractor}, pinning the
 * user-visible appearance contracts that are encoded structurally (and so are
 * easy to break on refactor without any test failing loudly):
 * <ul>
 *   <li>texture wins over X3DMaterial on the same ring;</li>
 *   <li>X3D {@code transparency} is flipped to a glTF/Cesium alpha and
 *       diffuseColor is passed through as authored sRGB;</li>
 *   <li>each polygon's UVs are normalized by {@code (floor(minU), floor(minV))}
 *       so georeferenced ranges land near the origin before the atlas.</li>
 * </ul>
 */
class AppearanceExtractorTest {

    @TempDir
    Path tempDir;

    private static final Name SQUARE_RING_THEME = Name.of("appearance");

    /** A unit-square polygon on the ground plane. */
    private static Polygon square() {
        return Polygon.of(LinearRing.of(List.of(
                Coordinate.of(0, 0, 0), Coordinate.of(1, 0, 0),
                Coordinate.of(1, 1, 0), Coordinate.of(0, 1, 0),
                Coordinate.of(0, 0, 0))));
    }

    /** Wrap surface-data into a Feature carrying one appearance. */
    private static Feature featureWith(Object... surfaceData) {
        Appearance appearance = Appearance.newInstance();
        for (Object sd : surfaceData) {
            appearance.addSurfaceData(SurfaceDataProperty.of((org.citydb.model.appearance.SurfaceData<?>) sd));
        }
        return Feature.of(Name.of("TestFeature"))
                .addAppearance(AppearanceProperty.of(SQUARE_RING_THEME, appearance));
    }

    private TextureStore textureStore() {
        return new TextureStore(tempDir);
    }

    @Test
    void textureBeatsMaterialOnTheSameRing() {
        Polygon poly = square();
        LinearRing ring = poly.getExteriorRing();

        ParameterizedTexture pt = ParameterizedTexture.newInstance();
        pt.setTextureImage(ExternalFile.of("tex.png"));
        pt.addTextureCoordinates(ring, List.of(
                TextureCoordinate.of(0f, 0f), TextureCoordinate.of(1f, 0f),
                TextureCoordinate.of(1f, 1f), TextureCoordinate.of(0f, 1f),
                TextureCoordinate.of(0f, 0f)));

        X3DMaterial material = X3DMaterial.newInstance()
                .setDiffuseColor(Color.of(1.0, 0.0, 0.0))
                .addTarget(poly);

        RingAppearance ra = AppearanceExtractor.extract(featureWith(pt, material), textureStore());

        // The ring is textured...
        assertNotNull(ra.ringTextureIds());
        assertTrue(ra.ringTextureIds().containsKey(ring));
        assertNotNull(ra.texCoords());
        assertTrue(ra.texCoords().containsKey(ring));
        // ...so its material colour is dropped (only ring -> colours empty -> null).
        assertNull(ra.ringColors());
    }

    @Test
    void x3dMaterialFlipsTransparencyToAlphaAndKeepsSrgb() {
        Polygon poly = square();
        LinearRing ring = poly.getExteriorRing();

        X3DMaterial material = X3DMaterial.newInstance()
                .setDiffuseColor(Color.of(0.2, 0.4, 0.6))
                .setTransparency(0.25) // -> alpha 0.75
                .addTarget(poly);

        RingAppearance ra = AppearanceExtractor.extract(featureWith(material), textureStore());

        assertNull(ra.texCoords());
        assertNotNull(ra.ringColors());
        assertArrayEquals(new float[]{0.2f, 0.4f, 0.6f, 0.75f}, ra.ringColors().get(ring), 1e-6f);
    }

    @Test
    void parameterizedTextureUVsAreNormalizedToOrigin() {
        Polygon poly = square();
        LinearRing ring = poly.getExteriorRing();

        // UVs offset well away from the origin: floor(minU)=10, floor(minV)=12.
        ParameterizedTexture pt = ParameterizedTexture.newInstance();
        pt.setTextureImage(ExternalFile.of("tex.png"));
        pt.addTextureCoordinates(ring, List.of(
                TextureCoordinate.of(10.3f, 12.7f), TextureCoordinate.of(11.3f, 12.7f),
                TextureCoordinate.of(11.3f, 13.7f), TextureCoordinate.of(10.3f, 13.7f)));

        RingAppearance ra = AppearanceExtractor.extract(featureWith(pt), textureStore());

        List<TextureCoordinate> uvs = ra.texCoords().get(ring);
        assertNotNull(uvs);
        // First UV shifted by (10, 12) -> (0.3, 0.7); the shift is uniform.
        assertEquals(0.3f, uvs.get(0).getS(), 1e-5f);
        assertEquals(0.7f, uvs.get(0).getT(), 1e-5f);
        // No UV is negative after the floor shift.
        for (TextureCoordinate uv : uvs) {
            assertTrue(uv.getS() >= 0f && uv.getT() >= 0f, "normalized UV must be non-negative");
        }
    }

    @Test
    void georeferencedTextureProjectsAffineUVsPerVertex() {
        Polygon poly = square();
        LinearRing ring = poly.getExteriorRing();

        // UV = orientation x (xy - ref); ref = (-1, -2), row-major matrix
        // [0.5 0.1; 0 0.25] -> u = 0.5*(x+1) + 0.1*(y+2), v = 0.25*(y+2).
        // Corners (0,0),(1,0),(1,1),(0,1),(0,0) ->
        // (0.7,0.5),(1.2,0.5),(1.3,0.75),(0.8,0.75),(0.7,0.5);
        // floor(minU)=floor(0.7)=0, floor(minV)=0 -> no normalization shift.
        GeoreferencedTexture gt = GeoreferencedTexture.newInstance()
                .setTextureImage(ExternalFile.of("geo.png"))
                .setReferencePoint(Point.of(Coordinate.of(-1, -2, 0)))
                .setOrientation(Matrix2x2.ofRowMajor(0.5, 0.1, 0.0, 0.25))
                .addTarget(poly);

        RingAppearance ra = AppearanceExtractor.extract(featureWith(gt), textureStore());

        assertNotNull(ra.texCoords());
        List<TextureCoordinate> uvs = ra.texCoords().get(ring);
        assertNotNull(uvs);
        assertEquals(5, uvs.size(), "one UV per ring point, closing duplicate included");
        float[][] expected = {
                {0.7f, 0.5f}, {1.2f, 0.5f}, {1.3f, 0.75f}, {0.8f, 0.75f}, {0.7f, 0.5f}};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], uvs.get(i).getS(), 1e-5f, "u at vertex " + i);
            assertEquals(expected[i][1], uvs.get(i).getT(), 1e-5f, "v at vertex " + i);
        }
        assertNotNull(ra.ringTextureIds());
        assertTrue(ra.ringTextureIds().containsKey(ring));
    }

    /**
     * A polygon targeted by both a ParameterizedTexture and a
     * GeoreferencedTexture must keep the PT's authored UVs and texture id
     * regardless of surface-data order (asymmetric put/putIfAbsent rule).
     */
    private void assertParameterizedTextureWins(boolean ptFirst) {
        Polygon poly = square();
        LinearRing ring = poly.getExteriorRing();

        ParameterizedTexture pt = ParameterizedTexture.newInstance();
        pt.setTextureImage(ExternalFile.of("pt.png"));
        pt.addTextureCoordinates(ring, List.of(
                TextureCoordinate.of(0f, 0f), TextureCoordinate.of(1f, 0f),
                TextureCoordinate.of(1f, 1f), TextureCoordinate.of(0f, 1f),
                TextureCoordinate.of(0f, 0f)));

        // GT would project u = 2x, v = 2y -> (2,0) and (2,2) at corners 1 and
        // 2, clearly distinct from the PT's authored [0,1] UVs.
        GeoreferencedTexture gt = GeoreferencedTexture.newInstance()
                .setTextureImage(ExternalFile.of("gt.png"))
                .setReferencePoint(Point.of(Coordinate.of(0, 0, 0)))
                .setOrientation(Matrix2x2.ofRowMajor(2.0, 0.0, 0.0, 2.0))
                .addTarget(poly);

        TextureStore store = textureStore();
        Feature feature = ptFirst ? featureWith(pt, gt) : featureWith(gt, pt);
        RingAppearance ra = AppearanceExtractor.extract(feature, store);

        List<TextureCoordinate> uvs = ra.texCoords().get(ring);
        assertNotNull(uvs);
        assertEquals(1f, uvs.get(1).getS(), 1e-5f, "PT's authored u must win (GT would project 2)");
        assertEquals(0f, uvs.get(1).getT(), 1e-5f);
        assertEquals(1f, uvs.get(2).getS(), 1e-5f);
        assertEquals(1f, uvs.get(2).getT(), 1e-5f, "PT's authored v must win (GT would project 2)");
        // register() dedups by URI, so this resolves the PT image's id.
        Integer ptId = store.register("pt.png");
        assertEquals(ptId, ra.ringTextureIds().get(ring),
                "the ring's texture id must be the PT's image");
    }

    @Test
    void parameterizedTextureWinsOverGeoreferencedWhenProcessedFirst() {
        assertParameterizedTextureWins(true);
    }

    @Test
    void parameterizedTextureWinsOverGeoreferencedWhenProcessedSecond() {
        assertParameterizedTextureWins(false);
    }

    @Test
    void featureWithoutAppearancesYieldsEmpty() {
        Feature feature = Feature.of(Name.of("TestFeature"));
        RingAppearance ra = AppearanceExtractor.extract(feature, textureStore());
        assertTrue(ra.isEmpty());
        assertFalse(ra.texCoords() != null || ra.ringColors() != null);
    }
}
