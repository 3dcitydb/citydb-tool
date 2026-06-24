/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.styling;

import org.citydb.model.common.Name;
import org.citydb.vis.VisExportException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the schema-independent surface of
 * {@link ObjectStyleRegistry}: the "tolerant of no schema" contract and the
 * {@code fromConfig} validation gate. The child-overrides-parent hierarchy walk
 * is deliberately <em>not</em> covered here — it needs a DB-backed
 * {@link org.citydb.database.schema.SchemaMapping} with a real
 * {@code FeatureType} hierarchy (the constructors are package-private and
 * {@code build} requires a live connection), so it belongs in an integration
 * test rather than a unit test. What is pinned below is everything reachable
 * without a schema, which is where the silent-misconfiguration risks live.
 */
class ObjectStyleRegistryTest {

    private static final Name BUILDING = Name.of("Building");

    @Test
    void emptyRegistryAlwaysResolvesToDefault() {
        ObjectStyleRegistry registry = ObjectStyleRegistry.empty();
        assertFalse(registry.hasOverrides());
        assertEquals(DefaultObjectStyle.defaults(), registry.defaultStyle());
        // Same default instance regardless of the requested type, incl. null.
        assertSame(registry.defaultStyle(), registry.resolve(BUILDING));
        assertSame(registry.defaultStyle(), registry.resolve(null));
    }

    @Test
    void overridesAreSilentlyIgnoredWithoutASchema() {
        // The registry tolerates being built without a SchemaMapping (non-CLI
        // entry points). Overrides cannot be indexed against a schema, so
        // resolve() must fall back to the default rather than blow up.
        DefaultObjectStyle red = DefaultObjectStyle.parseColor("#ff0000");
        ObjectStyleRegistry registry = ObjectStyleRegistry.builder()
                .override(BUILDING, red)
                .build(); // no schemaMapping(...)

        assertTrue(registry.hasOverrides());
        assertSame(registry.defaultStyle(), registry.resolve(BUILDING));
    }

    @Test
    void fromConfigReturnsEmptyWhenNothingConfigured() {
        ObjectStyleRegistry registry = assertDoesNotThrow(
                () -> ObjectStyleRegistry.fromConfig(null, null, null));
        assertFalse(registry.hasOverrides());
        assertEquals(DefaultObjectStyle.defaults(), registry.defaultStyle());
    }

    @Test
    void fromConfigAppliesDefaultColorWithoutASchema() {
        ObjectStyleRegistry registry = assertDoesNotThrow(
                () -> ObjectStyleRegistry.fromConfig("#102030", null, null));
        assertFalse(registry.hasOverrides());
        assertEquals(DefaultObjectStyle.parseColor("#102030"), registry.defaultStyle());
    }

    @Test
    void fromConfigRejectsBadDefaultColor() {
        assertThrows(VisExportException.class,
                () -> ObjectStyleRegistry.fromConfig("not-a-color", null, null));
    }

    @Test
    void fromConfigRejectsOverridesWithoutASchema() {
        // Per-feature-type overrides need a schema to resolve their keys; asking
        // for them without one is a loud error, not a silent drop.
        assertThrows(VisExportException.class,
                () -> ObjectStyleRegistry.fromConfig(null,
                        Map.of("bldg:Building", "#ff0000"), null));
    }
}
