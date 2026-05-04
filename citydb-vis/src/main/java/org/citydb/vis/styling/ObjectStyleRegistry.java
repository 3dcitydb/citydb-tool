/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.styling;

import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.model.common.Name;

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
