/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
