/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parser for the {@link AttributeProjection} mapping grammar:
 * <pre>
 *   mapping       := output_col ':' source
 *   source        := table '/' [ '[' aggregate ']' ] column_path [ '::' value_type ]
 *   table         := 'FEATURE' | 'ATTRIBUTES' | 'ADDRESS'
 *   aggregate     := 'FIRST' | 'LAST' | 'COUNT' | 'ALL'
 *   column_path   := segment ( '.' segment )*    (single segment for FEATURE; for ADDRESS a single segment optionally followed by '[' predicate ']')
 *   segment       := [ prefix ':' ] local_name [ '[' predicate ']' ]   (predicate only on ATTRIBUTES segments)
 *   predicate     := local_name '=' value
 *   value         := "'" string "'" | integer | decimal | 'true' | 'false'
 *   value_type    := 'int' | 'double' | 'string' | 'timestamp' | 'uri' | 'array' | 'code' | 'uom' | 'content' | 'mimeType'   (ATTRIBUTES only; 'array' emits a "; "-joined ArrayValue; 'code'/'uom'/'content'/'mimeType' expose Attribute metadata fields)
 * </pre>
 * For ATTRIBUTES, predicate scope is uniform: at any segment {@code X},
 * {@code X[k=v]} keeps only nodes named X whose child {@code k} equals
 * {@code v}. Compose along the path to filter at each step. For ADDRESS,
 * the predicate filters Address rows by matching another column's value.
 * <p>
 * Mappings come in as picocli-split tokens (one per element). For long
 * lists, picocli's built-in {@code @file} argument syntax expands each
 * non-blank line of a file into a separate CLI value. All parse errors
 * throw {@link IllegalArgumentException} with a leading
 * "&lt;location&gt;: ..." prefix so the controller can surface where the
 * bad input is.
 */
final class AttributeProjectionParser {
    private AttributeProjectionParser() {
    }

    static List<AttributeProjection.Mapping> parseInline(List<String> tokens) {
        List<AttributeProjection.Mapping> out = new ArrayList<>(tokens.size());
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < tokens.size(); i++) {
            String location = "--attributes[" + i + "]";
            AttributeProjection.Mapping mapping = parseMapping(tokens.get(i).trim(), location);
            if (!seen.add(mapping.outputColumn())) {
                throw new IllegalArgumentException(location + ": duplicate output column '"
                        + mapping.outputColumn() + "' inside --attributes (declared twice on the CLI).");
            }
            out.add(mapping);
        }
        return out;
    }

    static AttributeProjection.Mapping parseMapping(String raw, String location) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException(location + ": mapping must not be empty.");
        }
        int colon = raw.indexOf(':');
        if (colon < 1) {
            throw new IllegalArgumentException(location + ": missing ':' separating output column "
                    + "from source expression in '" + raw + "'.");
        }
        String outputColumn = raw.substring(0, colon).trim();
        validateOutputColumn(outputColumn, location);
        String sourceExpr = raw.substring(colon + 1).trim();
        AttributeProjection.Source source = parseSource(sourceExpr, location);
        return new AttributeProjection.Mapping(outputColumn, source);
    }

    private static void validateOutputColumn(String name, String location) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException(location + ": output column is empty.");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // ',' is forbidden because the CLI splits --attributes on commas;
            // an inline token containing ',' would be cut in two before the
            // parser ever sees it, producing confusing downstream errors.
            if (c == ':' || c == '/' || c == '[' || c == ']' || c == ',') {
                throw new IllegalArgumentException(location + ": output column '" + name +
                        "' contains a reserved character ('" + c + "').");
            }
        }
    }

    private static AttributeProjection.Source parseSource(String expr, String location) {
        int slash = expr.indexOf('/');
        if (slash < 1) {
            throw new IllegalArgumentException(location + ": missing '/' separating table from column "
                    + "in source '" + expr + "'.");
        }
        String table = expr.substring(0, slash).trim();
        String tail = expr.substring(slash + 1).trim();
        // Table names are control keywords (closed set, no collision with
        // data field names), so case-insensitive matching is friction-free.
        return switch (table.toUpperCase(Locale.ROOT)) {
            case "FEATURE" -> parseFeatureSource(tail, location);
            case "ATTRIBUTES" -> parseAttributesSource(tail, location);
            case "ADDRESS" -> parseAddressSource(tail, location);
            default -> throw new IllegalArgumentException(location + ": unknown table '" + table
                    + "' (expected FEATURE, ATTRIBUTES, or ADDRESS).");
        };
    }

    // ---- FEATURE ----

    private static AttributeProjection.Source parseFeatureSource(String tail, String location) {
        if (tail.startsWith("[")) {
            throw new IllegalArgumentException(location + ": FEATURE source must not declare an "
                    + "aggregate (FEATURE has a single row). Got '" + tail + "'.");
        }
        if (tail.indexOf('[') >= 0) {
            throw new IllegalArgumentException(location + ": FEATURE source must not declare a "
                    + "predicate (FEATURE has a single row). Got '" + tail + "'.");
        }
        if (tail.contains("::")) {
            throw new IllegalArgumentException(location + ": FEATURE source must not declare a "
                    + "value-type cast (FEATURE fields are statically typed). Got '" + tail + "'.");
        }
        if (tail.indexOf('.') >= 0) {
            throw new IllegalArgumentException(location + ": FEATURE source must reference a single "
                    + "field (no dotted paths). Got '" + tail + "'.");
        }
        FeatureField field = FeatureField.forName(tail);
        if (field == null) {
            throw new IllegalArgumentException(location + ": FEATURE field '" + tail + "' is not "
                    + "supported. Allowed: " + FeatureField.names() + ".");
        }
        return new AttributeProjection.FeatureSource(field);
    }

    // ---- ATTRIBUTES ----

    private static AttributeProjection.Source parseAttributesSource(String tail, String location) {
        AggregateAndRest ar = peelAggregate(tail, location);
        PathAndCast pc = peelValueTypeCast(ar.rest(), location);
        String pathRaw = pc.path();
        if (pathRaw.isEmpty()) {
            throw new IllegalArgumentException(location + ": ATTRIBUTES source has an empty column path.");
        }
        List<String> rawSegments = splitPathSegments(pathRaw, location);
        List<AttributeProjection.PathSegment> path = new ArrayList<>(rawSegments.size());
        for (String rawSegment : rawSegments) {
            path.add(parsePathSegment(rawSegment, pathRaw, location));
        }
        return new AttributeProjection.AttributesSource(path, ar.aggregate(), pc.valueType());
    }

    /**
     * Detect a trailing {@code ::type} value-type cast on an ATTRIBUTES
     * source. Scans left-to-right with string-awareness so {@code '::'}
     * inside a predicate's quoted value isn't mistaken for the cast
     * marker. Returns the path (everything before the cast) plus the
     * resolved {@link ValueType}, or null cast when no marker is found.
     */
    private static PathAndCast peelValueTypeCast(String raw, String location) {
        int castStart = findValueTypeCastMarker(raw);
        if (castStart < 0) {
            return new PathAndCast(raw, null);
        }
        String pathPart = raw.substring(0, castStart).trim();
        String castToken = raw.substring(castStart + 2).trim();
        if (castToken.isEmpty()) {
            throw new IllegalArgumentException(location + ": empty value-type cast after '::' in '"
                    + raw + "'.");
        }
        ValueType valueType = ValueType.forToken(castToken);
        if (valueType == null) {
            throw new IllegalArgumentException(location + ": unknown value-type cast '" + castToken
                    + "'. Allowed: " + ValueType.tokens() + ".");
        }
        return new PathAndCast(pathPart, valueType);
    }

    private static int findValueTypeCastMarker(String s) {
        boolean inString = false;
        int bracketDepth = 0;
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                inString = !inString;
            } else if (!inString && c == '[') {
                bracketDepth++;
            } else if (!inString && c == ']') {
                if (bracketDepth > 0) bracketDepth--;
            } else if (!inString && bracketDepth == 0 && c == ':' && s.charAt(i + 1) == ':') {
                return i;
            }
        }
        return -1;
    }

    private record PathAndCast(String path, ValueType valueType) {
    }

    /**
     * Split an ATTRIBUTES path on '.' while respecting bracketed
     * predicates and quoted strings: a '.' inside {@code [...]} or
     * inside {@code '...'} is part of a value, not a segment delimiter.
     * Throws on unbalanced brackets / unterminated strings.
     */
    private static List<String> splitPathSegments(String path, String location) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        int bracketDepth = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\'') {
                inString = !inString;
                current.append(c);
            } else if (!inString && c == '[') {
                bracketDepth++;
                current.append(c);
            } else if (!inString && c == ']') {
                if (bracketDepth == 0) {
                    throw new IllegalArgumentException(location + ": unmatched ']' in path '" + path + "'.");
                }
                bracketDepth--;
                current.append(c);
            } else if (!inString && bracketDepth == 0 && c == '.') {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (bracketDepth != 0) {
            throw new IllegalArgumentException(location + ": unterminated '[' in path '" + path + "'.");
        }
        if (inString) {
            throw new IllegalArgumentException(location + ": unterminated string literal in path '" + path + "'.");
        }
        segments.add(current.toString());
        return segments;
    }

    /**
     * Parse one path segment of the form {@code name} or
     * {@code name[predicate]}. Strips the optional namespace prefix
     * from the name (informational only); resolves the predicate via
     * the shared parser when present.
     */
    private static AttributeProjection.PathSegment parsePathSegment(String rawSegment, String fullPath,
                                                                    String location) {
        String segment = rawSegment.trim();
        if (segment.isEmpty()) {
            throw new IllegalArgumentException(location + ": ATTRIBUTES path has an empty segment "
                    + "(check leading/trailing or consecutive dots) in '" + fullPath + "'.");
        }
        int open = segment.indexOf('[');
        String namePart;
        AttributeProjection.Predicate predicate;
        if (open < 0) {
            namePart = segment;
            predicate = null;
        } else {
            if (!segment.endsWith("]")) {
                throw new IllegalArgumentException(location + ": ATTRIBUTES segment '" + segment
                        + "' has unexpected text after its predicate.");
            }
            namePart = segment.substring(0, open).trim();
            String predRaw = segment.substring(open + 1, segment.length() - 1).trim();
            predicate = parsePredicate(predRaw, location);
        }
        if (namePart.isEmpty()) {
            throw new IllegalArgumentException(location + ": ATTRIBUTES segment '" + segment
                    + "' has no localName before the predicate.");
        }
        // Optional namespace prefix is informational; strip after the LAST ':'.
        int sep = namePart.lastIndexOf(':');
        String local = sep >= 0 ? namePart.substring(sep + 1) : namePart;
        if (local.isEmpty()) {
            throw new IllegalArgumentException(location + ": ATTRIBUTES segment '" + segment
                    + "' is missing a local name after the prefix.");
        }
        return new AttributeProjection.PathSegment(local, predicate);
    }

    // ---- ADDRESS ----

    private static AttributeProjection.Source parseAddressSource(String tail, String location) {
        if (tail.contains("::")) {
            throw new IllegalArgumentException(location + ": ADDRESS source must not declare a "
                    + "value-type cast (Address fields are statically typed). Got '" + tail + "'.");
        }
        AggregateAndRest ar = peelAggregate(tail, location);
        ColumnAndPredicate cp = splitColumnAndPredicate(ar.rest(), location);
        if (cp.column().indexOf('.') >= 0) {
            throw new IllegalArgumentException(location + ": ADDRESS source must reference a single "
                    + "field (no dotted paths). Got '" + cp.column() + "'.");
        }
        AddressField field = AddressField.forName(cp.column());
        if (field == null) {
            throw new IllegalArgumentException(location + ": ADDRESS field '" + cp.column()
                    + "' is not supported. Allowed: " + AddressField.names() + ".");
        }
        if (cp.predicate() != null && AddressField.forName(cp.predicate().field()) == null) {
            throw new IllegalArgumentException(location + ": ADDRESS predicate field '"
                    + cp.predicate().field() + "' is not a known Address column. Allowed: "
                    + AddressField.names() + ".");
        }
        return new AttributeProjection.AddressSource(field, ar.aggregate(), cp.predicate());
    }

    // ---- aggregate prefix ----

    private record AggregateAndRest(AttributeProjection.Aggregate aggregate, String rest) {
    }

    private static AggregateAndRest peelAggregate(String tail, String location) {
        if (!tail.startsWith("[")) {
            return new AggregateAndRest(AttributeProjection.Aggregate.FIRST, tail);
        }
        int close = tail.indexOf(']');
        if (close < 0) {
            throw new IllegalArgumentException(location + ": unterminated '[' in aggregate of '" + tail + "'.");
        }
        String agg = tail.substring(1, close).trim();
        AttributeProjection.Aggregate aggregate;
        try {
            // Closed-set control keyword — accept any case so [first] and
            // [FIRST] both parse.
            aggregate = AttributeProjection.Aggregate.valueOf(agg.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(location + ": unknown aggregate '" + agg
                    + "' (expected FIRST, LAST, COUNT, or ALL).");
        }
        return new AggregateAndRest(aggregate, tail.substring(close + 1).trim());
    }

    // ---- predicate suffix ----

    private record ColumnAndPredicate(String column, AttributeProjection.Predicate predicate) {
    }

    private static ColumnAndPredicate splitColumnAndPredicate(String tail, String location) {
        int open = tail.indexOf('[');
        if (open < 0) {
            return new ColumnAndPredicate(tail.trim(), null);
        }
        int close = findUnquotedClosingBracket(tail, open + 1);
        if (close < 0) {
            throw new IllegalArgumentException(location + ": unterminated '[' in predicate of '" + tail + "'.");
        }
        if (close != tail.length() - 1) {
            throw new IllegalArgumentException(location + ": unexpected text after predicate in '" + tail + "'.");
        }
        String column = tail.substring(0, open).trim();
        String predRaw = tail.substring(open + 1, close).trim();
        AttributeProjection.Predicate predicate = parsePredicate(predRaw, location);
        return new ColumnAndPredicate(column, predicate);
    }

    private static int findUnquotedClosingBracket(String s, int start) {
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'') {
                inString = !inString;
            } else if (c == ']' && !inString) {
                return i;
            }
        }
        return -1;
    }

    private static AttributeProjection.Predicate parsePredicate(String raw, String location) {
        int eq = raw.indexOf('=');
        if (eq < 1) {
            throw new IllegalArgumentException(location + ": predicate '" + raw + "' is not of the form "
                    + "field=value.");
        }
        String field = raw.substring(0, eq).trim();
        if (field.isEmpty()) {
            throw new IllegalArgumentException(location + ": predicate field is empty in '" + raw + "'.");
        }
        Object value = parsePredicateValue(raw.substring(eq + 1).trim(), raw, location);
        return new AttributeProjection.Predicate(field, value);
    }

    private static Object parsePredicateValue(String raw, String full, String location) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(location + ": predicate '" + full + "' has an empty value.");
        }
        if (raw.charAt(0) == '\'') {
            if (raw.length() < 2 || raw.charAt(raw.length() - 1) != '\'') {
                throw new IllegalArgumentException(location + ": predicate string value '" + raw
                        + "' is not terminated with a closing quote.");
            }
            return raw.substring(1, raw.length() - 1);
        }
        if ("true".equalsIgnoreCase(raw)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        // Numeric: decimal/scientific → Double; integer → Long.
        boolean isDecimal = raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0;
        try {
            return isDecimal ? Double.parseDouble(raw) : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(location + ": predicate value '" + raw
                    + "' is not a quoted string, integer, decimal, or boolean (true/false).");
        }
    }
}
