/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Function;

public enum ValidityReference {
    DATABASE("database", "creationDate", "terminationDate", Feature::getCreationDate, Feature::getTerminationDate),
    REAL_WORLD("realWorld", "validFrom", "validTo", Feature::getValidFrom, Feature::getValidTo);

    private final String value;
    private final Name from;
    private final Name to;
    private final Function<Feature, Optional<OffsetDateTime>> fromTime;
    private final Function<Feature, Optional<OffsetDateTime>> toTime;

    ValidityReference(String value, String from, String to, Function<Feature, Optional<OffsetDateTime>> fromTime,
                      Function<Feature, Optional<OffsetDateTime>> toTime) {
        this.value = value;
        this.from = Name.of(from, Namespaces.CORE);
        this.to = Name.of(to, Namespaces.CORE);
        this.fromTime = fromTime;
        this.toTime = toTime;
    }

    public Name from() {
        return from;
    }

    public Name to() {
        return to;
    }

    public Optional<OffsetDateTime> from(Feature feature) {
        return fromTime.apply(feature);
    }

    public Optional<OffsetDateTime> to(Feature feature) {
        return toTime.apply(feature);
    }

    public String toValue() {
        return value;
    }

    public static ValidityReference fromValue(String value) {
        for (ValidityReference v : ValidityReference.values()) {
            if (v.value.equals(value)) {
                return v;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
