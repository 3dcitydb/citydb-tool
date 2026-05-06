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

import java.util.Map;

public class SceneOptions implements Option {
    @CommandLine.Option(names = "--grid-edge-length", paramLabel = "<meters>",
            defaultValue = "200.0",
            description = "Edge length in meters of one grid cell used as the leaf of the " +
                    "spatial aggregation tree (default: ${DEFAULT-VALUE}). Smaller values " +
                    "produce a finer grid and shorter camera load distances.")
    private double gridEdgeLength;

    @CommandLine.Option(names = "--lod-refine-radius", paramLabel = "<pixels>",
            defaultValue = "128.0",
            description = "Projected MBS radius (pixels) above which a tile refines to its " +
                    "children (default: ${DEFAULT-VALUE}). Applied uniformly to both 3D Tiles " +
                    "(via geometric error) and I3S (via LOD threshold) so both formats load " +
                    "the same level of detail at any given camera distance. Lower values " +
                    "load more detail, higher values lighten the viewer.")
    private double lodRefineRadius;

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
                    "the quadtree leaves once they cross the LOD threshold — smoothest " +
                    "LOD cascade. 'quadtree' runs the same split but drops the preview: " +
                    "the split cell root becomes a content-less intermediate, the " +
                    "runtime refines straight from the parent aggregation to the " +
                    "quadtree leaves (one fewer LOD level, faster export, sharper LOD " +
                    "transition). The few cells that cannot be subdivided further " +
                    "(single-feature / depth-cap residuals) are handled per " +
                    "--atlas-fallback in both quadtree-based modes. 'rescale' disables " +
                    "the split stage entirely and shrinks textures uniformly to fit " +
                    "one atlas page on every overflowing cell — implies " +
                    "--atlas-fallback=rescale regardless of an explicit value " +
                    "(legacy / debugging).")
    private AtlasOverflowMode atlasOverflowMode;

    @CommandLine.Option(names = "--atlas-fallback", paramLabel = "<strategy>",
            defaultValue = "expand",
            description = "How to resolve texture overflow on residual cells the " +
                    "quadtree stage could not subdivide further (single-feature or " +
                    "depth-cap fallback): ${COMPLETION-CANDIDATES} (default: " +
                    "${DEFAULT-VALUE}). 'expand' preserves source-resolution textures: " +
                    "3D Tiles spills onto additional atlas pages (multi-page GLB), I3S " +
                    "grows the single atlas page up to the WebGL 16K cap. 'rescale' " +
                    "honors --max-atlas-size by shrinking textures uniformly, accepting " +
                    "silent quality loss; 3D Tiles forces single-atlas mode (no multi-page). " +
                    "Ignored when --atlas-overflow-mode=rescale (which forces 'rescale' " +
                    "globally on every overflowing cell).")
    private AtlasFallbackStrategy atlasFallbackStrategy;

    @CommandLine.Option(names = "--enable-shading",
            description = "Emit per-vertex NORMAL so plain, X3DMaterial-coloured, and " +
                    "per-feature-type-styled surfaces render shaded (PBR + Lambertian) " +
                    "and pick up 3D form. Textured surfaces also receive NORMAL but " +
                    "with the local up-direction in place of the geometric normal, so " +
                    "walls and roofs stay at the same brightness within a node while " +
                    "still responding to time-of-day sun changes. Both 3D Tiles and " +
                    "I3S follow this behaviour. When omitted, every primitive renders " +
                    "unlit — smaller files, no shading.")
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

    public double getGridEdgeLength() {
        return gridEdgeLength;
    }

    public double getLodRefineRadius() {
        return lodRefineRadius;
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

    @Override
    public void preprocess(CommandLine commandLine) {
        if (gridEdgeLength <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --grid-edge-length must be a positive number of meters but was '" +
                            gridEdgeLength + "'");
        }

        if (lodRefineRadius <= 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --lod-refine-radius must be a positive number of pixels but was '" +
                            lodRefineRadius + "'");
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
