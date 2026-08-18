/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.operation.exporter.options.LodMode;
import org.citydb.operation.exporter.options.LodOptions;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

public class LodFilter {
    private final Set<String> lods;
    private final LodMode mode;
    private final boolean enabled;

    private String targetLod;
    private boolean hasRemovedGeometry;

    public LodFilter(LodOptions options) {
        Objects.requireNonNull(options, "The LoD filter options must not be null.");
        lods = options.getLods();
        mode = options.getMode();
        enabled = switch (mode) {
            case KEEP, REMOVE -> !lods.isEmpty();
            case MINIMUM, MAXIMUM -> true;
        };
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean requiresAvailableLods() {
        return mode == LodMode.MINIMUM || mode == LodMode.MAXIMUM;
    }

    public void setAvailableLods(Collection<String> availableLods) {
        if (mode == LodMode.MINIMUM || mode == LodMode.MAXIMUM) {
            targetLod = availableLods.stream()
                    .filter(lod -> lods.isEmpty() || lods.contains(lod))
                    .min(mode == LodMode.MINIMUM ? Comparator.naturalOrder() : Comparator.reverseOrder())
                    .orElse(null);
        } else {
            targetLod = null;
        }
    }

    boolean hasRemovedGeometry() {
        return hasRemovedGeometry;
    }

    public boolean filter(String lod) {
        if (enabled && lod != null) {
            boolean satisfied = switch (mode) {
                case KEEP -> lods.contains(lod);
                case REMOVE -> !lods.contains(lod);
                case MINIMUM, MAXIMUM -> targetLod != null && targetLod.equals(lod);
            };

            if (!satisfied) {
                hasRemovedGeometry = true;
            }

            return satisfied;
        } else {
            return true;
        }
    }

    public void reset() {
        targetLod = null;
        hasRemovedGeometry = false;
    }
}
