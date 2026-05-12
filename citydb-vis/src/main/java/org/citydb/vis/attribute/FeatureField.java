/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.model.feature.Feature;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The single source of truth for the {@code FEATURE/<field>} projection
 * surface. Each constant binds one user-facing field name (CLI grammar,
 * camelCase) to the {@code Optional}-unwrapping extractor on
 * {@link Feature}. Adding a new projectable first-class field = adding
 * one constant; the parser and encoder pick up the change automatically
 * via {@link #forName(String)} and {@link #names()}.
 */
enum FeatureField {
    OBJECTID("objectid", f -> f.getObjectId().orElse(null)),
    IDENTIFIER("identifier", f -> f.getIdentifier().orElse(null)),
    CREATION_DATE("creationDate",
            f -> f.getCreationDate().map(OffsetDateTime::toString).orElse(null)),
    TERMINATION_DATE("terminationDate",
            f -> f.getTerminationDate().map(OffsetDateTime::toString).orElse(null)),
    VALID_FROM("validFrom",
            f -> f.getValidFrom().map(OffsetDateTime::toString).orElse(null)),
    VALID_TO("validTo",
            f -> f.getValidTo().map(OffsetDateTime::toString).orElse(null)),
    LAST_MODIFICATION_DATE("lastModificationDate",
            f -> f.getLastModificationDate().map(OffsetDateTime::toString).orElse(null));

    // Case-insensitive lookup (matches the table / aggregate / value-type-cast
    // keywords). TreeMap keeps the canonical camelCase strings as keys so
    // names() / "Allowed: ..." error hints display the official spelling.
    private static final Map<String, FeatureField> BY_NAME;
    static {
        Map<String, FeatureField> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (FeatureField ff : values()) {
            map.put(ff.fieldName, ff);
        }
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private final String fieldName;
    private final Function<Feature, Object> extractor;

    FeatureField(String fieldName, Function<Feature, Object> extractor) {
        this.fieldName = fieldName;
        this.extractor = extractor;
    }

    String fieldName() {
        return fieldName;
    }

    Object extract(Feature feature) {
        return extractor.apply(feature);
    }

    /** Resolve the user-facing name (camelCase, case-insensitive) to a constant, or null if unknown. */
    static FeatureField forName(String name) {
        return name == null ? null : BY_NAME.get(name);
    }

    /** Snapshot of every supported field name, used by the parser for the "Allowed: ..." error hint. */
    static Set<String> names() {
        return BY_NAME.keySet();
    }
}
