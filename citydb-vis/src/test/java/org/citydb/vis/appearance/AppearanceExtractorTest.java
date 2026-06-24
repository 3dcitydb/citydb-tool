/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
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
    void featureWithoutAppearancesYieldsEmpty() {
        Feature feature = Feature.of(Name.of("TestFeature"));
        RingAppearance ra = AppearanceExtractor.extract(feature, textureStore());
        assertTrue(ra.isEmpty());
        assertFalse(ra.texCoords() != null || ra.ringColors() != null);
    }
}
