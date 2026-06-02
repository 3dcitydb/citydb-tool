/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.styling.DefaultObjectStyle;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;

public class SceneOptions implements Option {
    @CommandLine.Option(names = "--grid-edge-length", paramLabel = "<meters>",
            description = "Edge length in meters of one grid cell used as the leaf of the " +
                    "spatial aggregation tree. When omitted, the cell edge is sized to the " +
                    "longest side of the dataset extent so the entire dataset fits in a " +
                    "single root cell (no spatial subdivision). Set an explicit smaller " +
                    "value to produce a finer grid and shorter camera load distances.")
    private double gridEdgeLength;

    @CommandLine.Option(names = "--screen-pixel-threshold", paramLabel = "<pixels>",
            defaultValue = "56",
            description = "Projected MBS radius (pixels) above which a tile refines to its " +
                    "children (default: ${DEFAULT-VALUE}). Applied uniformly to both 3D Tiles " +
                    "(via geometric error) and I3S (via LOD threshold) so both formats load " +
                    "the same level of detail at any given camera distance. Lower values load " +
                    "more detail (heavier viewer), higher values defer refinement (lighter " +
                    "viewer). Pass 0 to always refine to the leaves; this can crash the viewer " +
                    "on city-scale datasets and is intended for small exports or debugging.")
    private double screenPixelThreshold;

    @CommandLine.Option(names = "--clamp-to-ground",
            description = "Place each building on the ellipsoid surface (height 0). " +
                    "Useful when no terrain is loaded in the viewer.")
    private boolean clampToGround;

    @CommandLine.Option(names = "--texture-scale", paramLabel = "<factor>",
            defaultValue = "1.0",
            description = "Texture resolution scale factor between 0.01 and 1.0 (default: ${DEFAULT-VALUE}). " +
                    "Lower values reduce texture size and improve loading speed in the viewer.")
    private double textureScale;

    @CommandLine.Option(names = "--max-atlas-size", paramLabel = "<pixels>",
            defaultValue = "1024",
            description = "Maximum texture atlas edge length in pixels, between 1024 and 16384 " +
                    "(default: ${DEFAULT-VALUE}). Higher values pack more textures per atlas " +
                    "but increase GPU memory usage and texture upload latency in the viewer " +
                    "(e.g., 2048² = 16 MB RGBA8 per atlas page, 4× more than 1024²).")
    private int maxAtlasSize;

    @CommandLine.Option(names = "--atlas-overflow-mode", paramLabel = "<mode>",
            defaultValue = "hybrid",
            description = "Strategy when a cell's textures exceed --max-atlas-size: " +
                    "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). " +
                    "'hybrid' subdivides the offending cell spatially (2x2 push-down) " +
                    "until each leaf fits one atlas page AND retains a low-resolution " +
                    "rescaled preview on each split cell root, replaced at runtime by " +
                    "the split leaves once they cross the LOD threshold — smoothest " +
                    "LOD cascade. 'split' runs the same subdivision but drops the preview: " +
                    "the split cell root becomes a content-less intermediate, the " +
                    "runtime refines straight from the parent aggregation to the " +
                    "split leaves (one fewer LOD level, faster export, sharper LOD " +
                    "transition). The few cells that cannot be subdivided further " +
                    "(single-feature / depth-cap residuals) are handled per " +
                    "--atlas-fallback in both split-based modes. 'flat' disables " +
                    "the split stage entirely: every overflowing cell is processed in " +
                    "place (no tree hierarchy introduced), with the outcome controlled by " +
                    "--atlas-fallback ('rescale' shrinks textures uniformly to fit " +
                    "--max-atlas-size; 'expand' grows the single I3S atlas up to 16K / " +
                    "spills onto multi-page 3D Tiles atlases).")
    private AtlasOverflowMode atlasOverflowMode;

    @CommandLine.Option(names = "--atlas-fallback", paramLabel = "<strategy>",
            defaultValue = "expand",
            description = "How to resolve texture overflow on cells the split " +
                    "stage could not (or did not) subdivide further — single-feature " +
                    "and depth-cap residuals under --atlas-overflow-mode=hybrid/split, " +
                    "or every overflowing cell under --atlas-overflow-mode=flat: " +
                    "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). 'expand' " +
                    "preserves source-resolution textures: 3D Tiles spills onto " +
                    "additional atlas pages (multi-page GLB), I3S grows the single " +
                    "atlas page up to the WebGL 16K cap. 'rescale' honors " +
                    "--max-atlas-size by shrinking textures uniformly, accepting " +
                    "silent quality loss; 3D Tiles forces single-atlas mode (no " +
                    "multi-page). Under --atlas-overflow-mode=hybrid the per-cell-root " +
                    "LOD preview always uses 'rescale' regardless of this flag, so " +
                    "the preview stays within --max-atlas-size.")
    private AtlasFallbackStrategy atlasFallbackStrategy;

    @CommandLine.Option(names = "--enable-shading",
            description = "Emit per-vertex NORMAL so plain, X3DMaterial-coloured, and " +
                    "per-feature-type-styled surfaces render shaded (PBR + Lambertian) " +
                    "and pick up 3D form. Textured surfaces also receive NORMAL but " +
                    "with the local up-direction in place of the geometric normal, so " +
                    "walls and roofs stay at the same brightness within a node while " +
                    "still responding to time-of-day sun changes. Both 3D Tiles and " +
                    "I3S follow this behaviour. When omitted, every primitive renders " +
                    "unlit — smaller files, no shading. " +
                    "Auto-enabled when I3S is exported with --slpk, since ArcGIS " +
                    "Pro / Online refuse to load a scene layer whose legacy geometry " +
                    "buffer omits NORMAL (the per-vertex stream is mis-parsed and " +
                    "the layer fails with a red error indicator). For folder-mode " +
                    "I3S export targeted at ArcGIS, pass this flag explicitly.")
    private boolean enableShading;

    @CommandLine.Option(names = "--default-color", paramLabel = "<#rrggbb[aa]>",
            description = "Default sRGB color applied to features that have neither a " +
                    "texture nor an X3DMaterial (default: opaque white). Form: '#rrggbb' " +
                    "or '#rrggbbaa'. Applies uniformly to every feature class on the " +
                    "no-appearance path (Building, Bridge, Tunnel, ...). Surfaces with an " +
                    "explicit texture or X3DMaterial keep their authored color and are " +
                    "not affected. Pair with --enable-shading to render these surfaces " +
                    "PBR-shaded; without it they are unlit.")
    private String defaultColor;

    @CommandLine.Option(names = "--feature-type-style", split = ",",
            paramLabel = "<type=#rrggbb[aa]>",
            description = "Per-feature-type sRGB color override on the no-appearance path " +
                    "(supported by both 3D Tiles and I3S). The key is a qualified feature " +
                    "type name like 'bldg:Building'. Child types take precedence over " +
                    "parents via the schema type hierarchy, so an override on " +
                    "'core:AbstractCityObject' acts as a default for every CityGML feature " +
                    "and a more specific override on 'bldg:Building' wins for buildings " +
                    "only. Multiple entries may be supplied either by repeating the option " +
                    "or with a comma-separated list, e.g. " +
                    "--feature-type-style bldg:Building=#ff0000,tran:Road=#808080cc.")
    private Map<String, String> featureTypeStyles;

    @CommandLine.Option(names = "--attributes", split = ",", paramLabel = "<col:src>",
            description = "Declarative whitelist of columns to emit on the per-feature " +
                    "attribute table. Each comma-separated entry has the form " +
                    "'<output_col>:<source>'. <source> is '<TABLE>/[<AGG>]<col_path>' where " +
                    "TABLE is FEATURE, ATTRIBUTES, or ADDRESS; AGG (optional, defaults to FIRST) " +
                    "is FIRST/LAST/COUNT/ALL. For FEATURE, <col_path> is a single field name. " +
                    "For ADDRESS, <col_path> is a single field optionally followed by " +
                    "'[<field>=<value>]' to filter address rows. For ATTRIBUTES, <col_path> is a " +
                    "dotted chain of localName segments where any segment may carry its own " +
                    "'[<field>=<value>]' predicate keeping only nodes whose child <field> equals " +
                    "<value>. Predicate <value> is a quoted string ('Munich'), integer, decimal, " +
                    "or boolean (true/false). ATTRIBUTES paths may end with '::<type>' to force " +
                    "the leaf value extraction to a specific Attribute value type — one of " +
                    "'int', 'double', 'string', 'timestamp', 'uri', 'array' (\"; \"-joined " +
                    "ArrayValue elements), or one of the metadata-side casts: 'code' " +
                    "(codeSpace URI of a CityGML coded value), 'uom' (unit of measure), " +
                    "'content' (generic content blob), 'mimeType' (mime type of the generic " +
                    "content). Without a cast the encoder auto-detects the present " +
                    "value type (int → double → string → timestamp → uri, with fallback into " +
                    "a 'value' sub-attribute). Examples: " +
                    "'OBJECTID:FEATURE/objectid', 'HEIGHT:ATTRIBUTES/measuredHeight', " +
                    "'CITY:ADDRESS/[FIRST]city', 'STREET:ADDRESS/[FIRST]street', " +
                    "'HOUSE_NUMBER:ADDRESS/[FIRST]houseNumber', " +
                    "'TARGET_RESOURCE:ATTRIBUTES/[FIRST]externalReference::uri'. " +
                    "Use picocli's '@file' syntax for long lists, e.g. --attributes @cols.txt " +
                    "(each non-blank line of <cols.txt> is one entry; '#' comments are NOT " +
                    "supported in @file mode). Case rules: TABLE, AGG, the '::type' cast, and " +
                    "FEATURE / ADDRESS field names are all case-insensitive; ATTRIBUTES path " +
                    "segments are case-sensitive (they match XML localNames literally). " +
                    "Without this option, every top-level attribute is exported " +
                    "(default behaviour).")
    private List<String> attributes;

    public double getGridEdgeLength() {
        return gridEdgeLength;
    }

    public double getScreenPixelThreshold() {
        return screenPixelThreshold;
    }

    public boolean isClampToGround() {
        return clampToGround;
    }

    public double getTextureScale() {
        return textureScale;
    }

    public int getMaxAtlasSize() {
        return maxAtlasSize;
    }

    public AtlasOverflowMode getAtlasOverflowMode() {
        return atlasOverflowMode;
    }

    public AtlasFallbackStrategy getAtlasFallbackStrategy() {
        return atlasFallbackStrategy;
    }

    public boolean isEnableShading() {
        return enableShading;
    }

    /**
     * Build a {@link DefaultObjectStyle} from the {@code --default-color}
     * CLI flag. The caller is expected to gate the call on
     * {@code Command.hasMatchedOption("--default-color", ...)} so the
     * writer's format-options-level default is not overwritten when no
     * flag was provided.
     */
    public DefaultObjectStyle getDefaultObjectStyle() {
        return defaultColor != null
                ? DefaultObjectStyle.parseColor(defaultColor)
                : DefaultObjectStyle.defaults();
    }

    /**
     * Raw {@code qualifiedName -> hex color} map from
     * {@code --feature-type-style}. The controller resolves each qualified
     * name against the {@link org.citydb.database.schema.SchemaMapping} and
     * builds the {@link org.citydb.vis.styling.ObjectStyleRegistry}; this
     * class only validates the hex syntax in {@link #preprocess}.
     * Returns an empty map when the option was not provided.
     */
    public Map<String, String> getFeatureTypeStyles() {
        return featureTypeStyles != null ? featureTypeStyles : Map.of();
    }

    /**
     * Raw mapping tokens from {@code --attributes}, already comma-split
     * by picocli. The controller hands these straight to
     * {@link org.citydb.vis.attribute.AttributeProjection#parse}. Empty
     * list when the option was not provided.
     */
    public List<String> getAttributes() {
        return attributes != null ? attributes : List.of();
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (gridEdgeLength < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --grid-edge-length must be a non-negative number of meters but was '" +
                            gridEdgeLength + "'");
        }

        if (screenPixelThreshold < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --screen-pixel-threshold must be a non-negative number of pixels but was '" +
                            screenPixelThreshold + "'");
        }

        if (textureScale < 0.01 || textureScale > 1.0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --texture-scale must be between 0.01 and 1.0 but was '" +
                            textureScale + "'");
        }

        if (maxAtlasSize < 1024 || maxAtlasSize > 16384) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --max-atlas-size must be between 1024 and 16384 but was '" +
                            maxAtlasSize + "'");
        }

        if (defaultColor != null) {
            try {
                DefaultObjectStyle.parseColor(defaultColor);
            } catch (IllegalArgumentException e) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --default-color " + e.getMessage());
            }
        }

        if (featureTypeStyles != null) {
            for (Map.Entry<String, String> e : featureTypeStyles.entrySet()) {
                try {
                    DefaultObjectStyle.parseColor(e.getValue());
                } catch (IllegalArgumentException ex) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: --feature-type-style for '" + e.getKey() + "' " + ex.getMessage());
                }
            }
        }
    }
}
