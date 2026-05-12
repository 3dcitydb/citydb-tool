/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.model.property.Attribute;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Optional value-type cast on an ATTRIBUTES leaf, written as
 * {@code ::type} at the end of the source expression
 * (e.g. {@code ATTRIBUTES/externalReference.uri::uri}).
 * <p>
 * When omitted, {@link AttributeEncoder} uses auto-detection: first
 * present value in priority order int → double → string → timestamp →
 * uri, with a fallback into a {@code "value"} sub-attribute. The cast
 * disables both — it pulls strictly the named value type off the leaf,
 * no recursion. Use it when an Attribute carries multiple value types
 * simultaneously (rare) or when the auto-priority would pick the
 * wrong one.
 * <p>
 * Each constant binds the user-facing token (CLI grammar, lowercase)
 * to the {@code Optional}-unwrapping extractor on {@link Attribute}.
 * Same enum-with-behavior pattern as {@link FeatureField} /
 * {@link AddressField} — single source of truth, no parallel switch.
 */
enum ValueType {
    INT("int", a -> a.getIntValue().orElse(null)),
    DOUBLE("double", a -> a.getDoubleValue().orElse(null)),
    STRING("string", a -> a.getStringValue().orElse(null)),
    // Timestamps render to their ISO-8601 string the same way the
    // auto-extractor does, so toggling on/off the cast doesn't change
    // the output type for a temporal attribute.
    TIMESTAMP("timestamp", a -> a.getTimeStamp().map(OffsetDateTime::toString).orElse(null)),
    URI("uri", a -> a.getURI().orElse(null)),
    // Array-typed attributes (e.g. gml:doubleList in elevation, free-text
    // address blocks) emit a "; "-joined string of raw element values via
    // AttributeEncoder.formatArrayValue — ArrayValue itself has no
    // toString() override, so this is the canonical rendering.
    ARRAY("array", a -> AttributeEncoder.formatArrayValue(a.getArrayValue().orElse(null))),
    // CityGML code-typed attributes (e.g. <roofType codeSpace="...">1000</roofType>)
    // carry the code value in stringValue and the code-list URI in
    // codeSpace. ::string already exposes the code itself; this cast
    // exposes its codeSpace — the otherwise hidden metadata side.
    CODE("code", a -> a.getCodeSpace().orElse(null)),
    // CityGML measure-typed attributes carry a numeric value in
    // intValue / doubleValue and the unit of measure (e.g. "m", "deg")
    // in uom. Pair this with ::int or ::double on a sibling column to
    // surface both halves of a measure.
    UOM("uom", a -> a.getUom().orElse(null)),
    // 3DCityDB's generic-content attribute pattern: an opaque blob with
    // an accompanying mime type. ::content yields the blob, ::mimeType
    // its type. Usually used together when the blob is binary / XML.
    CONTENT("content", a -> a.getGenericContent().orElse(null)),
    MIME_TYPE("mimeType", a -> a.getGenericContentMimeType().orElse(null));

    // TreeMap with String.CASE_INSENSITIVE_ORDER so lookup ignores case
    // (e.g. 'uri' / 'URI' / 'Uri' all resolve) while preserving the
    // canonical token strings ('mimeType' stays mixed-case) for display
    // in error messages via tokens().
    private static final Map<String, ValueType> BY_TOKEN;
    static {
        Map<String, ValueType> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ValueType vt : values()) {
            map.put(vt.token, vt);
        }
        BY_TOKEN = Collections.unmodifiableMap(map);
    }

    private final String token;
    private final Function<Attribute, Object> extractor;

    ValueType(String token, Function<Attribute, Object> extractor) {
        this.token = token;
        this.extractor = extractor;
    }

    String token() {
        return token;
    }

    Object extract(Attribute attr) {
        return extractor.apply(attr);
    }

    /**
     * Resolve the user-facing token (case-insensitive) or null if
     * unknown. Closed-set control keyword — accepts {@code "uri"},
     * {@code "URI"}, {@code "Uri"} all the same. {@code mimeType} is
     * the only mixed-case canonical token; case-insensitive matching
     * means users can also write {@code mimetype}, {@code MIMETYPE},
     * etc.
     */
    static ValueType forToken(String token) {
        return token == null ? null : BY_TOKEN.get(token);
    }

    /** Snapshot of every supported token, for the parser's "Allowed: ..." error hint. */
    static Set<String> tokens() {
        return BY_TOKEN.keySet();
    }
}
