/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import java.util.Map;

/**
 * Lightweight metadata for a processed city model feature.
 * <p>
 * The actual triangle mesh is persisted in a {@link MeshStore} and referenced
 * by {@link #meshHandle()}. This keeps heap usage low when millions of
 * features are accumulated during the write phase.
 */
record FeatureData(long id, String objectId, String featureType,
                   double centerX, double centerY, double[] bbox,
                   Map<String, Object> attributes, long meshHandle) {
}
