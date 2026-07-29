/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import org.citydb.vis.appearance.AtlasFallbackStrategy;
import org.citydb.vis.appearance.AtlasOverflowMode;
import org.citydb.vis.options.ClampMode;
import picocli.CommandLine;

import java.util.List;
import java.util.Map;

/**
 * Shell grouping the scene-related CLI options of the vis-export commands
 * into separately headed help sections (tiling, terrain, texture, styling,
 * attributes). The flat getters below delegate into the nested groups so the
 * controller can keep addressing a single facade.
 * <p>
 * picocli only instantiates a nested group when at least one of its options
 * is matched on the command line, so the delegating getters fall back to the
 * field-type default when the group is absent. This is safe because the
 * controller reads a value only after checking
 * {@code Command.hasMatchedOption(...)} for the corresponding flag — the
 * {@code defaultValue} attributes on the nested options exist for the
 * {@code ${DEFAULT-VALUE}} help text, not for the config-merge path.
 */
public class SceneOptions implements Option {
    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Tiling and LOD options:%n")
    private TilingOptions tilingOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Terrain options:%n")
    private TerrainOptions terrainOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Texture and atlas options:%n")
    private TextureOptions textureOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Styling options:%n")
    private StylingOptions stylingOptions;

    @CommandLine.ArgGroup(exclusive = false, order = Integer.MAX_VALUE,
            heading = "Attribute export options:%n")
    private AttributeOptions attributeOptions;

    public double getGridEdgeLength() {
        return tilingOptions != null ? tilingOptions.getGridEdgeLength() : 0;
    }

    public double getScreenPixelThreshold() {
        return tilingOptions != null ? tilingOptions.getScreenPixelThreshold() : 0;
    }

    public ClampMode getClampMode() {
        return terrainOptions != null ? terrainOptions.getClampMode() : null;
    }

    public String getCesiumIonToken() {
        return terrainOptions != null ? terrainOptions.getCesiumIonToken() : null;
    }

    public double getTextureScale() {
        return textureOptions != null ? textureOptions.getTextureScale() : 0;
    }

    public int getMaxAtlasSize() {
        return textureOptions != null ? textureOptions.getMaxAtlasSize() : 0;
    }

    public AtlasOverflowMode getAtlasOverflowMode() {
        return textureOptions != null ? textureOptions.getAtlasOverflowMode() : null;
    }

    public AtlasFallbackStrategy getAtlasFallbackStrategy() {
        return textureOptions != null ? textureOptions.getAtlasFallbackStrategy() : null;
    }

    public boolean isEnableShading() {
        return stylingOptions != null && stylingOptions.isEnableShading();
    }

    public String getDefaultColor() {
        return stylingOptions != null ? stylingOptions.getDefaultColor() : null;
    }

    public Map<String, String> getFeatureTypeStyles() {
        return stylingOptions != null ? stylingOptions.getFeatureTypeStyles() : Map.of();
    }

    public List<String> getAttributes() {
        return attributeOptions != null ? attributeOptions.getAttributes() : List.of();
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (tilingOptions != null) {
            tilingOptions.preprocess(commandLine);
        }

        if (terrainOptions != null) {
            terrainOptions.preprocess(commandLine);
        }

        if (textureOptions != null) {
            textureOptions.preprocess(commandLine);
        }

        if (stylingOptions != null) {
            stylingOptions.preprocess(commandLine);
        }

        if (attributeOptions != null) {
            attributeOptions.preprocess(commandLine);
        }
    }
}
