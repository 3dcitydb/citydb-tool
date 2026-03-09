/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Child;
import org.citydb.model.feature.Feature;

import java.util.Optional;

public interface SrsReference {
    Optional<Integer> getSRID();

    SrsReference setSRID(Integer srid);

    Optional<String> getSrsIdentifier();

    SrsReference setSrsIdentifier(String srsIdentifier);

    default SrsReference getInheritedSrsReference() {
        if (this instanceof Child parent) {
            while ((parent = parent.getParent().orElse(null)) != null) {
                if (parent instanceof SrsReference reference) {
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
