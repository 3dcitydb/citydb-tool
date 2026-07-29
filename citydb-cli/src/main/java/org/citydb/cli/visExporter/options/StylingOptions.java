/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import org.citydb.vis.styling.DefaultObjectStyle;
import picocli.CommandLine;

import java.util.Map;

public class StylingOptions implements Option {
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

    public boolean isEnableShading() {
        return enableShading;
    }

    /**
     * Raw {@code --default-color} hex string ({@code #rrggbb[aa]}), or
     * {@code null} when the flag was not provided. The controller copies
     * this onto the format options, which build the
     * {@link org.citydb.vis.styling.ObjectStyleRegistry} in
     * {@link org.citydb.vis.options.VisFormatOptions#buildStyleRegistry};
     * this class only validates the hex syntax in {@link #preprocess}.
     */
    public String getDefaultColor() {
        return defaultColor;
    }

    /**
     * Raw {@code qualifiedName -> hex color} map from
     * {@code --feature-type-style}. The controller copies it onto the format
     * options, which resolve each qualified name against the
     * {@link org.citydb.database.schema.SchemaMapping} and build the
     * {@link org.citydb.vis.styling.ObjectStyleRegistry}; this class only
     * validates the hex syntax in {@link #preprocess}.
     * Returns an empty map when the option was not provided.
     */
    public Map<String, String> getFeatureTypeStyles() {
        return featureTypeStyles != null ? featureTypeStyles : Map.of();
    }

    @Override
    public void preprocess(CommandLine commandLine) {
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
