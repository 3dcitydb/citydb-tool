/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.attribute.AttributeProjection;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the CLI ↔ JSON-config bridge on {@link VisFormatOptions}: styling and
 * attribute projection are configured as plain strings (which fastjson2 can
 * round-trip) and only turned into their runtime objects by
 * {@link VisFormatOptions#buildStyleRegistry} /
 * {@link VisFormatOptions#buildAttributeProjection}, which the CLI controller
 * calls after merging config and flags.
 * <p>
 * Two things have to hold for that split to work, and neither fails loudly if
 * it breaks: the raw strings must survive serialization while the derived
 * runtime objects must stay out of the JSON, and the options must be usable
 * (non-null registry, null projection) <em>before</em> either build method runs
 * — every writer reads {@code getStyleRegistry()} unconditionally.
 * <p>
 * The schema-backed half of {@code buildStyleRegistry} is covered by
 * {@code ObjectStyleRegistryTest} and the integration tests; only what is
 * reachable without a live {@code SchemaMapping} is pinned here.
 */
class VisFormatOptionsResolveTest {

    private static Tiles3DFormatOptions options() {
        return new Tiles3DFormatOptions();
    }

    private static Tiles3DFormatOptions roundTrip(VisFormatOptions options) {
        return JSON.parseObject(JSON.toJSONString(options), Tiles3DFormatOptions.class,
                JSONReader.Feature.UseDoubleForDecimals);
    }

    // ---- defaults before any build call ------------------------------------

    @Test
    void freshOptionsAreUsableBeforeEitherBuildMethodRuns() {
        Tiles3DFormatOptions options = options();

        // Writers read the registry unconditionally — it must never be null,
        // even on a code path that never calls buildStyleRegistry.
        assertNotNull(options.getStyleRegistry());
        assertFalse(options.getStyleRegistry().hasOverrides());
        assertEquals(DefaultObjectStyle.defaults(), options.getStyleRegistry().defaultStyle());
        // null projection is the "export every top-level attribute" sentinel.
        assertNull(options.getAttributeProjection());
    }

    // ---- serialization split -----------------------------------------------

    @Test
    void rawStringInputsRoundTripThroughJson() {
        Tiles3DFormatOptions options = options();
        options.setDefaultColor("#102030");
        options.setFeatureTypeStyles(Map.of("con:RoofSurface", "#ff0000cc"));
        options.setAttributes(List.of("h:ATTRIBUTES/height", "city:ADDRESS/city"));

        Tiles3DFormatOptions loaded = roundTrip(options);

        assertEquals("#102030", loaded.getDefaultColor());
        assertEquals(Map.of("con:RoofSurface", "#ff0000cc"), loaded.getFeatureTypeStyles());
        assertEquals(List.of("h:ATTRIBUTES/height", "city:ADDRESS/city"), loaded.getAttributes());
    }

    @Test
    void derivedRuntimeObjectsAreExcludedFromJsonAndNotRestoredByParsing()
            throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setDefaultColor("#102030");
        options.setAttributes(List.of("h:ATTRIBUTES/height"));
        options.buildStyleRegistry(null);
        options.buildAttributeProjection();
        assertNotNull(options.getAttributeProjection());

        JSONObject json = JSON.parseObject(JSON.toJSONString(options));

        // fastjson2 cannot round-trip a SchemaMapping-holding registry or a
        // sealed Source tree, so both must be absent from the written config.
        assertFalse(json.containsKey("styleRegistry"));
        assertFalse(json.containsKey("attributeProjection"));

        // Reloading yields the raw strings but NOT the runtime objects — the
        // caller is required to rebuild them after merging CLI overrides.
        Tiles3DFormatOptions loaded = roundTrip(options);
        assertEquals("#102030", loaded.getDefaultColor());
        assertNull(loaded.getAttributeProjection());
        assertFalse(loaded.getStyleRegistry().defaultStyle().hasNonDefaultColor(),
                "the registry must come back at its default until rebuilt");
    }

    // ---- buildAttributeProjection ------------------------------------------

    @Test
    void buildAttributeProjectionParsesTokensInDeclarationOrder() throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setAttributes(List.of("h:ATTRIBUTES/height", "city:ADDRESS/city"));

        options.buildAttributeProjection();

        AttributeProjection projection = options.getAttributeProjection();
        assertNotNull(projection);
        assertEquals(List.of("h", "city"), projection.declaredOutputColumns());
    }

    @Test
    void buildAttributeProjectionLeavesNullForNullOrEmptyInput() throws VisExportException {
        Tiles3DFormatOptions options = options();

        options.buildAttributeProjection();
        assertNull(options.getAttributeProjection(), "no --attributes means no projection");

        options.setAttributes(List.of());
        options.buildAttributeProjection();
        assertNull(options.getAttributeProjection(), "an empty list is the same sentinel as null");
    }

    @Test
    void buildAttributeProjectionTranslatesParserFailuresIntoTheVisExceptionModel()
            throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setAttributes(List.of("broken"));

        // The parser signals bad input with IllegalArgumentException; the config
        // boundary must convert it so the CLI reports one exception family.
        VisExportException ex = assertThrows(VisExportException.class,
                options::buildAttributeProjection);
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertEquals(ex.getCause().getMessage(), ex.getMessage(),
                "the parser's reason must reach the user unchanged; the caller adds attribution");

        // Correcting the input and rebuilding must clear the failed state.
        options.setAttributes(List.of("h:ATTRIBUTES/height"));
        options.buildAttributeProjection();
        assertEquals(List.of("h"), options.getAttributeProjection().declaredOutputColumns());
    }

    @Test
    void buildAttributeProjectionIsIdempotentAndReflectsLateOverrides()
            throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setAttributes(List.of("h:ATTRIBUTES/height"));

        options.buildAttributeProjection();
        options.buildAttributeProjection();
        assertEquals(List.of("h"), options.getAttributeProjection().declaredOutputColumns());

        // CLI > config: the controller overwrites the raw list, then rebuilds.
        options.setAttributes(List.of("a:ATTRIBUTES/a", "b:ATTRIBUTES/b"));
        options.buildAttributeProjection();
        assertEquals(List.of("a", "b"), options.getAttributeProjection().declaredOutputColumns());
    }

    // ---- buildStyleRegistry ------------------------------------------------

    @Test
    void buildStyleRegistryAppliesTheDefaultColorWithoutASchema() throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setDefaultColor("#ff8000cc");

        options.buildStyleRegistry(null);

        assertEquals(DefaultObjectStyle.parseColor("#ff8000cc"),
                options.getStyleRegistry().defaultStyle());
        assertFalse(options.getStyleRegistry().hasOverrides());
    }

    @Test
    void buildStyleRegistryWithNothingConfiguredKeepsTheDefaultRegistry()
            throws VisExportException {
        Tiles3DFormatOptions options = options();

        options.buildStyleRegistry(null);

        assertNotNull(options.getStyleRegistry());
        assertEquals(DefaultObjectStyle.defaults(), options.getStyleRegistry().defaultStyle());
        assertFalse(options.getStyleRegistry().hasOverrides());
    }

    @Test
    void buildStyleRegistryRejectsBadInputAndLeavesThePriorRegistryInPlace()
            throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setDefaultColor("#102030");
        options.buildStyleRegistry(null);

        options.setDefaultColor("not-a-color");
        assertThrows(VisExportException.class, () -> options.buildStyleRegistry(null));
        assertEquals(DefaultObjectStyle.parseColor("#102030"),
                options.getStyleRegistry().defaultStyle(),
                "a failed rebuild must not leave the options with a null registry");

        // Per-feature-type styles cannot be resolved without a schema.
        options.setDefaultColor(null);
        options.setFeatureTypeStyles(Map.of("con:RoofSurface", "#ff0000"));
        assertThrows(VisExportException.class, () -> options.buildStyleRegistry(null));
    }

    @Test
    void buildStyleRegistryIsIdempotentAndReflectsLateOverrides() throws VisExportException {
        Tiles3DFormatOptions options = options();
        options.setDefaultColor("#102030");

        options.buildStyleRegistry(null);
        options.buildStyleRegistry(null);
        assertEquals(DefaultObjectStyle.parseColor("#102030"),
                options.getStyleRegistry().defaultStyle());

        options.setDefaultColor("#00ff00");
        options.buildStyleRegistry(null);
        assertEquals(DefaultObjectStyle.parseColor("#00ff00"),
                options.getStyleRegistry().defaultStyle());
    }

    // ---- setter guards that config input can trip --------------------------

    @Test
    void nullEnumsFallBackToTheirDefaultsRatherThanNullingTheField() {
        // A config file that omits (or explicitly nulls) these keys must not
        // leave the writer with a null mode to switch on.
        Tiles3DFormatOptions options = options();
        options.setAtlasOverflowMode(null);
        options.setAtlasFallbackStrategy(null);

        assertEquals(AtlasOverflowMode.HYBRID, options.getAtlasOverflowMode());
        assertEquals(AtlasFallbackStrategy.EXPAND, options.getAtlasFallbackStrategy());
    }

    @Test
    void numericOptionsAreClampedToTheirSupportedRange() {
        Tiles3DFormatOptions options = options();

        // textureScale is a fraction; maxAtlasSize is bounded by what the
        // atlas builder and GPU consumers can actually handle.
        assertEquals(1.0, options.setTextureScale(4.0).getTextureScale());
        assertEquals(0.01, options.setTextureScale(-1.0).getTextureScale());
        assertEquals(0.5, options.setTextureScale(0.5).getTextureScale());

        assertEquals(16384, options.setMaxAtlasSize(99999).getMaxAtlasSize());
        assertEquals(1024, options.setMaxAtlasSize(1).getMaxAtlasSize());
        assertEquals(4096, options.setMaxAtlasSize(4096).getMaxAtlasSize());
    }

    @Test
    void clampedValuesSurviveTheJsonRoundTrip() {
        Tiles3DFormatOptions options = options();
        options.setTextureScale(0.25);
        options.setMaxAtlasSize(4096);
        options.setEnableShading(true);
        options.setGridEdgeLength(1200.0);
        options.setScreenPixelThreshold(0.0);

        Tiles3DFormatOptions loaded = roundTrip(options);

        assertEquals(0.25, loaded.getTextureScale());
        assertEquals(4096, loaded.getMaxAtlasSize());
        assertTrue(loaded.isEnableShading());
        assertEquals(1200.0, loaded.getGridEdgeLength());
        // 0 is the always-refine sentinel: it must not be swallowed as "unset"
        // and silently replaced by the 56 px default on reload.
        assertEquals(0.0, loaded.getScreenPixelThreshold());
    }
}
