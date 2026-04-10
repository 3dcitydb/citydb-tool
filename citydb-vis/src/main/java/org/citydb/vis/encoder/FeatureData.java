/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.encoder;

import java.util.Map;

/**
 * Lightweight metadata for a processed city model feature, used during
 * the close phase for per-node feature/attribute output.
 */
public record FeatureData(long id, String objectId, String featureType,
                          Map<String, Object> attributes) {
}
