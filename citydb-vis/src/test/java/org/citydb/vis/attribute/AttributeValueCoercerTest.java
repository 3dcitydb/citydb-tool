/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.vis.model.AttrType;
import org.citydb.vis.model.FeatureData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for {@link AttributeValueCoercer#dispatchByType}, the
 * shared type→extractor routing the I3S and 3D Tiles attribute encoders now
 * both delegate to. Pins which branch each {@link AttrType} takes (notably
 * that OID and INT share the int path) and that the extracted values reach the
 * right writer with the right coercion.
 */
class AttributeValueCoercerTest {

    private static List<FeatureData> features() {
        return List.of(
                new FeatureData(7L, "gml-1", "Building",
                        Map.of("h", 12, "area", 3.5, "name", "Town Hall")),
                new FeatureData(8L, "gml-2", "Building",
                        Map.of("h", 4, "area", 1.25, "name", "Shed")));
    }

    /** Marker the lambdas return so the test can assert which branch ran. */
    private enum Branch { INT, DOUBLE, UTF8 }

    private static Branch branchFor(AttrType type, String name) {
        return AttributeValueCoercer.dispatchByType(type, features(), name,
                v -> Branch.INT, v -> Branch.DOUBLE, v -> Branch.UTF8);
    }

    @Test
    void oidAndIntBothRouteToIntPath() {
        assertEquals(Branch.INT, branchFor(AttrType.OID, "OID"));
        assertEquals(Branch.INT, branchFor(AttrType.INT, "h"));
    }

    @Test
    void doubleAndStringRouteToTheirPaths() {
        assertEquals(Branch.DOUBLE, branchFor(AttrType.DOUBLE, "area"));
        assertEquals(Branch.UTF8, branchFor(AttrType.STRING, "name"));
    }

    @Test
    void intPathReceivesCoercedValues() {
        int[] got = AttributeValueCoercer.dispatchByType(AttrType.INT, features(), "h",
                v -> v, v -> null, v -> null);
        assertArrayEquals(new int[]{12, 4}, got);
    }

    @Test
    void oidPathReceivesTheSequentialId() {
        // OID resolves to FeatureData.id() (the Esri integer OID), not a user field.
        int[] got = AttributeValueCoercer.dispatchByType(AttrType.OID, features(), "OID",
                v -> v, v -> null, v -> null);
        assertArrayEquals(new int[]{7, 8}, got);
    }

    @Test
    void doublePathReceivesCoercedValues() {
        double[] got = AttributeValueCoercer.dispatchByType(AttrType.DOUBLE, features(), "area",
                v -> null, v -> v, v -> null);
        assertArrayEquals(new double[]{3.5, 1.25}, got, 1e-9);
    }

    @Test
    void stringPathReceivesUtf8Bytes() {
        byte[][] got = AttributeValueCoercer.dispatchByType(AttrType.STRING, features(), "name",
                v -> null, v -> null, v -> v);
        assertEquals("Town Hall", new String(got[0], java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("Shed", new String(got[1], java.nio.charset.StandardCharsets.UTF_8));
    }
}
