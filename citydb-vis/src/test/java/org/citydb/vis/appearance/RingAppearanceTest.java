/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.MultiSurface;
import org.citydb.model.geometry.Polygon;
import org.citydb.vis.geometry.ImplicitInstanceTransformer;
import org.citydb.vis.geometry.RingAttributes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two seams {@code RingAppearance} exists for.
 * <p>
 * {@link RingAppearance#forTriangulation()} is the bridge that lets
 * {@code vis.geometry} consume appearance data without depending on this
 * package; {@link RingAppearance#remapKeys} is the implicit-instance bridge
 * that rewrites a prototype's appearance onto the ring identities produced by
 * {@code ImplicitInstanceTransformer}'s deep copy.
 * <p>
 * Both fail silently: {@code remapKeys} drops entries whose key has no
 * counterpart in the ring map, so a broken bridge does not throw — it exports
 * untextured, uncolored instanced geometry. The last test therefore runs the
 * real transformer end to end rather than a hand-built map.
 */
class RingAppearanceTest {

    private static final float[] RED = {1f, 0f, 0f, 1f};
    private static final List<TextureCoordinate> UVS = List.of(
            TextureCoordinate.of(0f, 0f), TextureCoordinate.of(1f, 0f),
            TextureCoordinate.of(1f, 1f), TextureCoordinate.of(0f, 0f));

    // ---- empty contract ----------------------------------------------------

    @Test
    void emptinessIgnoresTextureIdsBecauseTheyAreUselessWithoutUvs() {
        assertTrue(RingAppearance.empty().isEmpty());
        // A texture id with no UV coordinates cannot produce a textured
        // triangle, so it must not keep the appearance "non-empty".
        assertTrue(new RingAppearance(null, Map.of(ring(), 7), null).isEmpty());

        assertFalse(new RingAppearance(Map.of(ring(), UVS), null, null).isEmpty());
        assertFalse(new RingAppearance(null, null, Map.of(ring(), RED)).isEmpty());
    }

    @Test
    void emptyIsASharedSingletonAndShortCircuitsRemapping() {
        assertSame(RingAppearance.empty(), RingAppearance.empty());
        // An empty appearance never consults the ring map — passing a map that
        // would blow up on lookup proves the short circuit.
        assertSame(RingAppearance.empty(), RingAppearance.empty().remapKeys(null));
        // Texture-ids-only is empty by the rule above, so it takes the same path
        // and loses its ids. Pinned because it is the surprising half of the rule.
        RingAppearance idsOnly = new RingAppearance(null, Map.of(ring(), 7), null);
        assertSame(RingAppearance.empty(), idsOnly.remapKeys(null));
    }

    // ---- forTriangulation --------------------------------------------------

    @Test
    void forTriangulationPassesAllThreeMapsThroughUnchanged() {
        LinearRing ring = ring();
        Map<LinearRing, List<TextureCoordinate>> uvs = Map.of(ring, UVS);
        Map<LinearRing, Integer> ids = Map.of(ring, 3);
        Map<LinearRing, float[]> colors = Map.of(ring, RED);

        RingAttributes attributes = new RingAppearance(uvs, ids, colors).forTriangulation();

        // Same instances, not copies — the geometry package reads through to
        // the very maps the extractor produced.
        assertSame(uvs, attributes.texCoords());
        assertSame(ids, attributes.ringTextureIds());
        assertSame(colors, attributes.ringColors());
    }

    @Test
    void forTriangulationPreservesNullMapsSoConsumersCanNullCheck() {
        RingAttributes attributes = RingAppearance.empty().forTriangulation();

        assertNull(attributes.texCoords());
        assertNull(attributes.ringTextureIds());
        assertNull(attributes.ringColors());
    }

    // ---- remapKeys ---------------------------------------------------------

    @Test
    void remapKeysRewritesEveryMapOntoTheTargetRingIdentities() {
        LinearRing proto = ring();
        LinearRing instance = ring();
        RingAppearance source = new RingAppearance(
                Map.of(proto, UVS), Map.of(proto, 3), Map.of(proto, RED));

        RingAppearance remapped = source.remapKeys(Map.of(proto, instance));

        assertEquals(1, remapped.texCoords().size());
        assertSame(UVS, remapped.texCoords().get(instance));
        assertEquals(3, remapped.ringTextureIds().get(instance));
        assertArrayEquals(RED, remapped.ringColors().get(instance));
        // The prototype key is gone — the instance's rings are the only ones
        // the downstream triangulator will ever look up.
        assertNull(remapped.texCoords().get(proto));
        // Values are shared, not copied: remapping is a key rewrite only.
        assertSame(RED, remapped.ringColors().get(instance));
    }

    @Test
    void remapKeysDropsEntriesWithNoCounterpartInTheRingMap() {
        LinearRing bridged = ring();
        LinearRing orphan = ring();
        LinearRing instance = ring();
        Map<LinearRing, Integer> ids = new IdentityHashMap<>();
        ids.put(bridged, 1);
        ids.put(orphan, 2);
        Map<LinearRing, List<TextureCoordinate>> uvs = new IdentityHashMap<>();
        uvs.put(bridged, UVS);
        uvs.put(orphan, UVS);

        RingAppearance remapped = new RingAppearance(uvs, ids, null)
                .remapKeys(Map.of(bridged, instance));

        // Silent drop by design: every prototype ring is expected to have a
        // counterpart, so a missing one is a bug upstream, not here.
        assertEquals(1, remapped.texCoords().size());
        assertEquals(1, remapped.ringTextureIds().size());
        assertSame(UVS, remapped.texCoords().get(instance));
        assertEquals(1, remapped.ringTextureIds().get(instance));
    }

    @Test
    void remapKeysPreservesNullMapsAsNullPerMap() {
        LinearRing proto = ring();
        LinearRing instance = ring();
        // Colors present, UVs and ids absent: the null-per-map contract must
        // survive the rewrite so consumers keep their single null check.
        RingAppearance remapped = new RingAppearance(null, null, Map.of(proto, RED))
                .remapKeys(Map.of(proto, instance));

        assertNull(remapped.texCoords());
        assertNull(remapped.ringTextureIds());
        assertArrayEquals(RED, remapped.ringColors().get(instance));
    }

    @Test
    void remapKeysAndItsResultMatchRingsByIdentityNotStructure() {
        LinearRing proto = ring();
        LinearRing structuralTwin = ring(); // same coordinates, different object
        LinearRing instance = ring();

        // A value-keyed ring map must not bridge a structurally identical ring.
        Map<LinearRing, LinearRing> valueKeyed = new HashMap<>();
        valueKeyed.put(structuralTwin, instance);
        RingAppearance missed = new RingAppearance(Map.of(proto, UVS), null, null)
                .remapKeys(valueKeyed);
        assertTrue(missed.texCoords().isEmpty(),
                "rings are bridged by object identity, never by coordinate equality");

        // ... and the rewritten map is identity-keyed too, so a lookup with a
        // structural twin of the instance ring misses.
        RingAppearance hit = new RingAppearance(Map.of(proto, UVS), null, null)
                .remapKeys(Map.of(proto, instance));
        assertSame(UVS, hit.texCoords().get(instance));
        assertNull(hit.texCoords().get(ring()));
    }

    @Test
    void remappingAcrossTheRealTransformerReachesTheClonedRings() {
        // The failure this guards: ImplicitInstanceTransformer deep-copies the
        // prototype, so the instance carries brand-new LinearRing objects. The
        // appearance was extracted against the PROTOTYPE rings; without the
        // ringMap rewrite every lookup misses and the instance exports
        // untextured. A shared polygon is used because the copy collapses it to
        // one clone — the remap must still land on that single identity.
        Polygon polygon = Polygon.of(ring());
        MultiSurface prototype = MultiSurface.of(polygon, polygon);
        LinearRing protoRing = polygon.getExteriorRing();

        RingAppearance appearance = new RingAppearance(
                Map.of(protoRing, UVS), Map.of(protoRing, 5), Map.of(protoRing, RED));

        ImplicitInstanceTransformer.Result result = ImplicitInstanceTransformer.copy(prototype);
        RingAppearance instanceAppearance = appearance.remapKeys(result.ringMap());

        MultiSurface copy = (MultiSurface) result.geometry();
        LinearRing clonedRing = copy.getPolygons().get(0).getExteriorRing();
        assertSame(clonedRing, copy.getPolygons().get(1).getExteriorRing());

        assertSame(UVS, instanceAppearance.texCoords().get(clonedRing));
        assertEquals(5, instanceAppearance.ringTextureIds().get(clonedRing));
        assertArrayEquals(RED, instanceAppearance.ringColors().get(clonedRing));
        assertNull(instanceAppearance.texCoords().get(protoRing),
                "the prototype identity must not survive into the instance appearance");
    }

    // ---- fixture -----------------------------------------------------------

    /** A fresh closed triangle ring; every call returns a distinct object. */
    private static LinearRing ring() {
        return LinearRing.of(List.of(
                Coordinate.of(0, 0, 0),
                Coordinate.of(1, 0, 0),
                Coordinate.of(1, 1, 0),
                Coordinate.of(0, 0, 0)));
    }
}
