/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.config;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.vis.VisExportException;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.attribute.AttributeProjection;
import org.citydb.vis.styling.ObjectStyleRegistry;

import java.util.List;
import java.util.Map;

/**
 * Base format options shared by all visualization export formats (I3S, 3D Tiles, etc.).
 * <p>
 * Subclasses add format-specific options (e.g., I3S node page constants,
 * 3D Tiles geometric error strategy) while inheriting the common spatial
 * partitioning and texture handling parameters.
 */
public abstract class VisFormatOptions implements OutputFormatOptions {
    // 0 is a sentinel for "auto": PartitioningStage treats it as a single
    // root cell sized to the dataset extent's longest side (no spatial
    // subdivision). Override with a positive value for a finer grid.
    private double gridEdgeLength = 0.0;
    // Default: refine when projected MBS radius exceeds 56 px — safe for
    // city-scale data. 0 is a sentinel both writers translate into an
    // always-refine policy (every leaf loads immediately); useful for
    // small exports / debugging but easily crashes the viewer on large
    // datasets, so users must opt in explicitly with --screen-pixel-threshold=0.
    private double screenPixelThreshold = 56.0;
    // null = no vertical clamping (features keep their absolute database height).
    // ELLIPSOID / CESIUM_WORLD_TERRAIN select an active clamp target. Serialized
    // by its kebab-case value ("ellipsoid" / "cesium-world-terrain") so the JSON
    // config spelling matches the CLI --clamp-to-ground spelling exactly, rather
    // than fastjson2's default enum-constant-name form (CESIUM_WORLD_TERRAIN).
    @JSONField(serializeUsing = ClampMode.JsonWriter.class, deserializeUsing = ClampMode.JsonReader.class)
    private ClampMode clampMode;
    private double textureScale = 1.0;
    private int maxAtlasSize = 1024;
    private AtlasOverflowMode atlasOverflowMode = AtlasOverflowMode.HYBRID;
    private AtlasFallbackStrategy atlasFallbackStrategy = AtlasFallbackStrategy.EXPAND;
    private boolean enableShading;

    // Serializable string inputs for the derived styling/attribute objects
    // below. These are what a JSON config file (or the CLI) actually
    // carries; the runtime ObjectStyleRegistry / AttributeProjection are
    // built from them by buildStyleRegistry() / buildAttributeProjection()
    // and are themselves excluded from serialization (they hold a
    // SchemaMapping and a sealed-type tree that fastjson2 cannot round-trip).
    // Keeping the raw strings in the config gives --default-color /
    // --feature-type-style / --attributes a clean JSON home with full CLI parity.
    private String defaultColor;
    private Map<String, String> featureTypeStyles;
    private List<String> attributes;

    @JSONField(serialize = false, deserialize = false)
    private ObjectStyleRegistry styleRegistry = ObjectStyleRegistry.empty();
    @JSONField(serialize = false, deserialize = false)
    private AttributeProjection attributeProjection;

    // Cesium ion access token for Cesium World Terrain clamping. Stored in
    // plaintext in the config file, matching how the database password is
    // handled (org.citydb.database.connection.ConnectionDetails#password) — the
    // CLI controller merges it with precedence CLI flag > config > CESIUM_ION_TOKEN.
    private String cesiumIonToken;

    public double getGridEdgeLength() {
        return gridEdgeLength;
    }

    public VisFormatOptions setGridEdgeLength(double gridEdgeLength) {
        this.gridEdgeLength = gridEdgeLength;
        return this;
    }

    public double getScreenPixelThreshold() {
        return screenPixelThreshold;
    }

    public VisFormatOptions setScreenPixelThreshold(double screenPixelThreshold) {
        this.screenPixelThreshold = screenPixelThreshold;
        return this;
    }

    /**
     * Active vertical clamp target, or {@code null} for no clamping (features
     * keep their absolute database height — the default).
     */
    public ClampMode getClampMode() {
        return clampMode;
    }

    public VisFormatOptions setClampMode(ClampMode clampMode) {
        this.clampMode = clampMode;
        return this;
    }

    /**
     * Cesium ion access token used to fetch Cesium World Terrain when
     * {@link #getClampMode()} is {@link ClampMode#CESIUM_WORLD_TERRAIN}. Stored
     * in plaintext in the config file (like the database password); the CLI
     * controller resolves the effective value with precedence
     * {@code --cesium-ion-token} > config > {@code CESIUM_ION_TOKEN}.
     * {@code null}/blank when not supplied.
     */
    public String getCesiumIonToken() {
        return cesiumIonToken;
    }

    public VisFormatOptions setCesiumIonToken(String cesiumIonToken) {
        this.cesiumIonToken = cesiumIonToken;
        return this;
    }

    public double getTextureScale() {
        return textureScale;
    }

    public VisFormatOptions setTextureScale(double textureScale) {
        this.textureScale = Math.max(0.01, Math.min(1.0, textureScale));
        return this;
    }

    public int getMaxAtlasSize() {
        return maxAtlasSize;
    }

    public VisFormatOptions setMaxAtlasSize(int maxAtlasSize) {
        this.maxAtlasSize = Math.max(1024, Math.min(16384, maxAtlasSize));
        return this;
    }

    public AtlasOverflowMode getAtlasOverflowMode() {
        return atlasOverflowMode;
    }

    public VisFormatOptions setAtlasOverflowMode(AtlasOverflowMode atlasOverflowMode) {
        this.atlasOverflowMode = atlasOverflowMode != null ? atlasOverflowMode : AtlasOverflowMode.HYBRID;
        return this;
    }

    public AtlasFallbackStrategy getAtlasFallbackStrategy() {
        return atlasFallbackStrategy;
    }

    public VisFormatOptions setAtlasFallbackStrategy(AtlasFallbackStrategy atlasFallbackStrategy) {
        this.atlasFallbackStrategy = atlasFallbackStrategy != null ? atlasFallbackStrategy : AtlasFallbackStrategy.EXPAND;
        return this;
    }

    /**
     * When {@code true}, every primitive carries a NORMAL attribute and
     * renders PBR-shaded — both 3D Tiles GLB and I3S behave identically.
     * Plain, X3DMaterial-colored, and per-feature-type-styled paths use
     * the polygon's real geometric normal so Lambertian darkening provides
     * 3D form. Textured paths use the local "up" direction instead of the
     * geometric normal so walls and roofs in the same node end up equally
     * lit (no per-face dimming on back-facing walls), while still
     * responding to time-of-day sun changes as a group. On I3S, white-pixel
     * sentinel triangles in mixed-feature textured nodes keep their real
     * geometric normal so they pick up proper PBR shading.
     * <p>
     * When {@code false} (default), NORMAL is dropped on every path and
     * primitives render unlit ({@code KHR_materials_unlit} on the GLB
     * side; NORMAL absence on the I3S side).
     */
    public boolean isEnableShading() {
        return enableShading;
    }

    public VisFormatOptions setEnableShading(boolean enableShading) {
        this.enableShading = enableShading;
        return this;
    }

    /**
     * Default sRGB color (form {@code #rrggbb} or {@code #rrggbbaa}) applied
     * to features on the no-appearance path (CLI {@code --default-color}).
     * {@code null} means opaque white. Consumed by {@link #resolve} to build
     * the {@link #styleRegistry}.
     */
    public String getDefaultColor() {
        return defaultColor;
    }

    public VisFormatOptions setDefaultColor(String defaultColor) {
        this.defaultColor = defaultColor;
        return this;
    }

    /**
     * Per-feature-type {@code qualifiedName -> hex color} overrides on the
     * no-appearance path (CLI {@code --feature-type-style}). Keys must be
     * qualified feature type names like {@code bldg:Building}; resolved
     * against the schema hierarchy by {@link #resolve}.
     */
    public Map<String, String> getFeatureTypeStyles() {
        return featureTypeStyles;
    }

    public VisFormatOptions setFeatureTypeStyles(Map<String, String> featureTypeStyles) {
        this.featureTypeStyles = featureTypeStyles;
        return this;
    }

    /**
     * Raw {@code --attributes} mapping tokens, one per element. Parsed into
     * the {@link #attributeProjection} by {@link #resolve}. {@code null} or
     * empty means "no projection — export every top-level attribute".
     */
    public List<String> getAttributes() {
        return attributes;
    }

    public VisFormatOptions setAttributes(List<String> attributes) {
        this.attributes = attributes;
        return this;
    }

    /**
     * Build the runtime {@link #styleRegistry} from the serialized string
     * inputs ({@link #getDefaultColor}, {@link #getFeatureTypeStyles}),
     * passing the {@link SchemaMapping} of the target database so
     * per-feature-type style keys can be resolved against the schema type
     * hierarchy. Call once after the config has been loaded and any CLI
     * overrides applied; idempotent.
     * <p>
     * The thrown message names the offending concept ("default color",
     * "feature type style") but deliberately not the CLI flag — the caller
     * (which knows whether the value came from the CLI or a config file)
     * adds that attribution.
     *
     * @throws VisExportException if a color string is malformed or a feature
     *                            type style key is not a known qualified name
     */
    public void buildStyleRegistry(SchemaMapping schemaMapping) throws VisExportException {
        styleRegistry = ObjectStyleRegistry.fromConfig(defaultColor, featureTypeStyles, schemaMapping);
    }

    /**
     * Parse {@link #getAttributes} into the runtime {@link #attributeProjection}.
     * Call once after the config has been loaded and any CLI override applied;
     * idempotent. {@code null}/empty input yields a {@code null} projection
     * ("export every top-level attribute"). The thrown message carries only the
     * parser's reason; the caller adds the source attribution.
     *
     * @throws VisExportException if an attribute mapping token cannot be parsed
     */
    public void buildAttributeProjection() throws VisExportException {
        try {
            attributeProjection = AttributeProjection.parse(attributes);
        } catch (IllegalArgumentException e) {
            throw new VisExportException(e.getMessage(), e);
        }
    }

    public ObjectStyleRegistry getStyleRegistry() {
        return styleRegistry;
    }

    /**
     * Optional declarative whitelist of columns emitted to the
     * per-feature attribute table (CLI {@code --attributes}).
     * {@code null} means "no projection, export every top-level
     * attribute" — the default behaviour.
     */
    public AttributeProjection getAttributeProjection() {
        return attributeProjection;
    }
}
