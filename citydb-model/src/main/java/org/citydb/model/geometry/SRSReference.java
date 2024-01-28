/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.model.geometry;

import org.citydb.model.common.Child;
import org.citydb.model.feature.Feature;

import java.util.Optional;

public interface SRSReference {
    Optional<Integer> getSRID();
    SRSReference setSRID(Integer srid);
    Optional<String> getSrsName();
    SRSReference setSrsName(String srsName);

    default SRSReference getInheritedSRSReference() {
        if (this instanceof Child parent) {
            while ((parent = parent.getParent().orElse(null)) != null) {
                if (parent instanceof SRSReference reference) {
                    return reference;
                } else if (parent instanceof Feature feature) {
                    Envelope envelope = feature.getEnvelope().orElse(null);
                    if (envelope != this && envelope != null) {
                        return envelope;
                    }
                }
            }
        }

        return null;
    }
}
