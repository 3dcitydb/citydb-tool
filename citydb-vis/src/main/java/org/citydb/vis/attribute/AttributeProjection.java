/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Declarative whitelist for the per-feature attribute table. Each
 * {@link Mapping} produces ONE output column on the I3S / 3D Tiles
 * attribute table; the column's value is pulled per-feature from one
 * of three logical "tables" backed by the in-memory {@code Feature}
 * model — {@code FEATURE} (first-class fields), {@code ATTRIBUTES}
 * (the attribute tree, with dotted nested paths and per-segment
 * child-scoped predicates), and {@code ADDRESS} (per-feature address
 * records, filterable by another column).
 * <p>
 * Modeled on the spreadsheet plugin's balloon template syntax
 * (e.g. {@code OUTPUT_COL:ADDRESS/[FIRST]street[city='Munich']}),
 * adapted to the runtime Feature object rather than SQL tables.
 * Driven exclusively by the {@code --attributes} CLI option (parsed
 * by {@link AttributeProjectionParser}); use picocli's
 * {@code @file} argument syntax for long lists. Consumed per-feature
 * by {@link AttributeEncoder}.
 */
public final class AttributeProjection {
    private final List<Mapping> mappings;

    public AttributeProjection(List<Mapping> mappings) {
        this.mappings = List.copyOf(mappings);
    }

    public List<Mapping> mappings() {
        return mappings;
    }

    /**
     * Parse picocli-split CLI tokens, one mapping per element. Returns
     * {@code null} when {@code tokens} is empty so the caller can use
     * {@code null} as the "no projection installed" sentinel. See
     * {@link AttributeProjectionParser} for the grammar.
     * <p>
     * For long mapping lists, use picocli's built-in {@code @file}
     * argument syntax (each non-blank line becomes one CLI value).
     * Note that {@code @file} does not honour {@code #} comments —
     * keep the file pure mapping lines.
     */
    public static AttributeProjection parse(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        return new AttributeProjection(AttributeProjectionParser.parseInline(tokens));
    }

    /**
     * Output column names in declaration order. Used by
     * {@link AttributeEncoder#finalizeFields} both to preserve the
     * user-typed column order in the attribute schema and to fire the
     * zero-match WARN at finalize time (declared but never populated).
     */
    public List<String> declaredOutputColumns() {
        List<String> names = new ArrayList<>(mappings.size());
        for (Mapping mapping : mappings) {
            names.add(mapping.outputColumn());
        }
        return names;
    }

    /**
     * One column on the attribute table: a user-chosen
     * {@code outputColumn} bound to a {@link Source} that knows how to
     * pull a value from a {@code Feature}.
     */
    public record Mapping(String outputColumn, Source source) {
        public Mapping {
            Objects.requireNonNull(outputColumn, "outputColumn");
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * Sealed root for the three logical tables. Each variant captures
     * exactly the inputs its evaluator needs — feature-field name,
     * attribute path with aggregate / predicate, or address field with
     * aggregate / predicate. Sealing prevents the parser and evaluator
     * from drifting apart on table-specific options.
     */
    public sealed interface Source permits FeatureSource, AttributesSource, AddressSource {
    }

    /**
     * {@code FEATURE/<field>}. Single-row source — aggregate and
     * predicate are rejected at parse time. The {@link FeatureField}
     * enum is the single source of truth for both the user-facing
     * name and the per-feature extractor.
     */
    public record FeatureSource(FeatureField field) implements Source {
        public FeatureSource {
            Objects.requireNonNull(field, "field");
        }
    }

    /**
     * {@code ATTRIBUTES/[AGG]a[k=v].b[k=v].c[::type]}. {@code path} is
     * one or more {@link PathSegment}s — a localName plus an optional
     * predicate scoping the segment's own children. {@code aggregate}
     * defaults to {@link Aggregate#FIRST}. {@code valueType} is the
     * optional explicit cast applied at the leaf; {@code null} means
     * "auto-detect" (int → double → string → timestamp → uri, with
     * recursive fall-through into a {@code "value"} sub-attribute).
     * <p>
     * Predicate scope is uniform: at any segment {@code X}, the
     * predicate {@code [k=v]} keeps only those nodes named {@code X}
     * that carry a child Attribute named {@code k} with value
     * {@code v}. Composes naturally — multiple predicates along the
     * path act as independent child-existence filters at each step.
     */
    public record AttributesSource(List<PathSegment> path,
                                   Aggregate aggregate,
                                   ValueType valueType) implements Source {
        public AttributesSource {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(aggregate, "aggregate");
            if (path.isEmpty()) {
                throw new IllegalArgumentException("AttributesSource path must not be empty.");
            }
            path = List.copyOf(path);
        }
    }

    /**
     * One step of an {@link AttributesSource} path. {@code localName}
     * matches an Attribute by its localName at the current level;
     * {@code predicate} (nullable) is applied immediately after
     * matching and keeps only those Attributes that carry a child
     * named {@code predicate.field()} with the predicate's value.
     */
    public record PathSegment(String localName, Predicate predicate) {
        public PathSegment {
            Objects.requireNonNull(localName, "localName");
            if (localName.isEmpty()) {
                throw new IllegalArgumentException("PathSegment localName must not be empty.");
            }
        }
    }

    /**
     * {@code ADDRESS/[AGG]<field>[k=v]}. {@code field} is the
     * {@link AddressField} enum constant the parser resolved.
     * {@code aggregate} defaults to {@link Aggregate#FIRST}.
     * {@code predicate} is nullable; predicate's {@code field} string
     * is validated at parse time against {@link AddressField#names()}
     * and resolved per-row at evaluation time.
     */
    public record AddressSource(AddressField field,
                                Aggregate aggregate,
                                Predicate predicate) implements Source {
        public AddressSource {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(aggregate, "aggregate");
        }
    }

    public enum Aggregate {
        /** First match (default when no bracket given). Null when no rows survive. */
        FIRST,
        /** Last match. Null when no rows survive. */
        LAST,
        /** Number of surviving rows as a {@code Long}. Always produces a value (0 included). */
        COUNT,
        /** {@code "; "}-joined string of every surviving row's value. Null when no rows survive. */
        ALL
    }

    /**
     * Equality filter. {@code field} is the name of a child Attribute
     * (ATTRIBUTES — the predicate is scoped to its segment's children)
     * or another Address column (ADDRESS — the predicate filters
     * Address rows). {@code value} is one of: {@code String},
     * {@code Long}, {@code Double}, {@code Boolean}. Any two
     * {@link Number} values compare via {@code doubleValue()} (so a
     * {@code Long} literal matches a {@code Double} actual and vice
     * versa); other classes require exact-class {@code equals}.
     */
    public record Predicate(String field, Object value) {
        public Predicate {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(value, "value");
            if (!(value instanceof String || value instanceof Long
                    || value instanceof Double || value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "Predicate value must be String, Long, Double, or Boolean; got "
                                + value.getClass().getSimpleName());
            }
        }
    }

}
