/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.styling;

import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.model.common.Name;
import org.citydb.model.common.PrefixedName;
import org.citydb.vis.VisExportException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@link DefaultObjectStyle} that applies to a given feature
 * type, with child types overriding parent types via the schema-defined
 * type hierarchy.
 * <p>
 * The registry holds raw user-supplied overrides keyed by the qualified
 * feature type {@link Name}. Resolution walks the type hierarchy from the
 * requested type up toward the root (via
 * {@link FeatureType#getTypeHierarchy()}) and returns the first override
 * encountered. If none is found, the configured default style is returned.
 * Results are memoized so each feature type incurs the hierarchy walk at
 * most once per export.
 * <p>
 * The registry is intentionally tolerant of being constructed without a
 * {@link SchemaMapping} (e.g. when used from non-CLI entry points that do
 * not bind to a 3DCityDB instance). In that case overrides cannot be
 * indexed against the schema and {@link #resolve(Name)} always returns the
 * default style — overrides are silently ignored.
 * <p>
 * Per-feature-type styling is consumed by the 3D Tiles writer only. The
 * I3S writer uses {@link #defaultStyle()} directly because its node-level
 * material model cannot accommodate per-feature material variation within
 * a node.
 */
public final class ObjectStyleRegistry {
    private final Map<Name, DefaultObjectStyle> overrides;
    private final DefaultObjectStyle defaultStyle;
    private final SchemaMapping schemaMapping;
    private final Map<Name, DefaultObjectStyle> cache = new ConcurrentHashMap<>();

    private ObjectStyleRegistry(Map<Name, DefaultObjectStyle> overrides,
                                DefaultObjectStyle defaultStyle,
                                SchemaMapping schemaMapping) {
        this.overrides = Map.copyOf(overrides);
        this.defaultStyle = Objects.requireNonNull(defaultStyle, "defaultStyle");
        this.schemaMapping = schemaMapping;
    }

    /**
     * Registry with no overrides — every {@link #resolve(Name)} call
     * returns {@link DefaultObjectStyle#defaults()}. Safe to use from
     * code paths that do not configure styling.
     */
    public static ObjectStyleRegistry empty() {
        return new ObjectStyleRegistry(Map.of(), DefaultObjectStyle.defaults(), null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Build a registry from the raw string inputs carried by a JSON config
     * file or the CLI ({@code --default-color} / {@code --feature-type-style}).
     * This is the single place that turns user-supplied strings into a
     * validated registry, so both entry points behave identically.
     * <p>
     * Returns {@link #empty()} when neither a default color nor any
     * per-feature-type override is given. Otherwise each override key is
     * required to be a qualified feature type name (e.g. {@code bldg:Building})
     * that resolves against {@code schemaMapping}; a bare local name, an
     * unknown type, or a malformed color is rejected loudly rather than
     * silently ignored — the styling space wants precision over convenience.
     *
     * @param defaultColor      sRGB hex string ({@code #rrggbb[aa]}) or {@code null}
     * @param featureTypeStyles {@code qualifiedName -> hex color} map or {@code null}
     * @param schemaMapping     schema used to resolve override keys; must be
     *                          non-null when {@code featureTypeStyles} is non-empty
     * @throws VisExportException on a malformed color, an unqualified or
     *                            unknown feature type key, or a missing schema
     */
    public static ObjectStyleRegistry fromConfig(String defaultColor,
                                                 Map<String, String> featureTypeStyles,
                                                 SchemaMapping schemaMapping) throws VisExportException {
        boolean hasDefault = defaultColor != null;
        boolean hasOverrides = featureTypeStyles != null && !featureTypeStyles.isEmpty();
        if (!hasDefault && !hasOverrides) {
            return empty();
        }

        Builder builder = builder().schemaMapping(schemaMapping);
        if (hasDefault) {
            builder.defaultStyle(parseColor(defaultColor, "default color", null));
        }

        if (hasOverrides) {
            if (schemaMapping == null) {
                throw new VisExportException("Resolving per-feature-type styles requires a database schema.");
            }
            for (Map.Entry<String, String> e : featureTypeStyles.entrySet()) {
                // Strict qualified-name match: typos like 'building' or
                // 'bldgg:Building' fail loudly rather than silently picking
                // the wrong type via a local-name fallback.
                PrefixedName name = PrefixedName.of(e.getKey());
                if (name.getPrefix().isEmpty()) {
                    throw new VisExportException("Feature type style key '" + e.getKey() +
                            "' must be a qualified name with a namespace prefix (e.g. 'bldg:Building').");
                }
                FeatureType featureType = schemaMapping.getFeatureType(name);
                if (featureType == FeatureType.UNDEFINED) {
                    throw new VisExportException("Feature type style references unknown feature type '" +
                            e.getKey() + "'.");
                }
                builder.override(featureType.getName(), parseColor(e.getValue(), "feature type style", e.getKey()));
            }
        }

        return builder.build();
    }

    private static DefaultObjectStyle parseColor(String hex, String what, String key) throws VisExportException {
        try {
            return DefaultObjectStyle.parseColor(hex);
        } catch (IllegalArgumentException e) {
            String where = key != null ? " for '" + key + "'" : "";
            throw new VisExportException("Invalid " + what + where + ": " + e.getMessage(), e);
        }
    }

    /**
     * Resolve the style applicable to {@code typeName}. Walks the schema
     * type hierarchy from the requested type to the root and returns the
     * first matching override; falls back to the configured default style.
     * Returns the default style without consulting the hierarchy when the
     * registry was built without a schema mapping or when {@code typeName}
     * is {@code null}.
     */
    public DefaultObjectStyle resolve(Name typeName) {
        if (typeName == null || schemaMapping == null || overrides.isEmpty()) {
            return defaultStyle;
        }
        return cache.computeIfAbsent(typeName, this::computeStyle);
    }

    private DefaultObjectStyle computeStyle(Name typeName) {
        FeatureType featureType = schemaMapping.getFeatureType(typeName);
        if (featureType == FeatureType.UNDEFINED) {
            return defaultStyle;
        }
        for (FeatureType current : featureType.getTypeHierarchy()) {
            DefaultObjectStyle style = overrides.get(current.getName());
            if (style != null) {
                return style;
            }
        }
        return defaultStyle;
    }

    public DefaultObjectStyle defaultStyle() {
        return defaultStyle;
    }

    /**
     * Whether the registry carries any per-feature-type overrides. The
     * I3S writer reads this at scene-layer build time to decide whether
     * to allocate the styled-colored material/geometry slots; the 3D
     * Tiles writer doesn't need it (its plain bucket logic handles a
     * single-style registry as a degenerate case).
     */
    public boolean hasOverrides() {
        return !overrides.isEmpty();
    }

    public static final class Builder {
        private final Map<Name, DefaultObjectStyle> overrides = new LinkedHashMap<>();
        private DefaultObjectStyle defaultStyle = DefaultObjectStyle.defaults();
        private SchemaMapping schemaMapping;

        private Builder() {
        }

        public Builder schemaMapping(SchemaMapping schemaMapping) {
            this.schemaMapping = schemaMapping;
            return this;
        }

        public Builder defaultStyle(DefaultObjectStyle defaultStyle) {
            if (defaultStyle != null) {
                this.defaultStyle = defaultStyle;
            }
            return this;
        }

        public Builder override(Name typeName, DefaultObjectStyle style) {
            Objects.requireNonNull(typeName, "typeName");
            Objects.requireNonNull(style, "style");
            overrides.put(typeName, style);
            return this;
        }

        public ObjectStyleRegistry build() {
            return new ObjectStyleRegistry(overrides, defaultStyle, schemaMapping);
        }
    }
}
