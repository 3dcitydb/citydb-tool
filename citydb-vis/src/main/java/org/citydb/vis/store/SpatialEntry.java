/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.store;

import java.nio.ByteBuffer;

/**
 * Compact spatial metadata for a processed feature.
 * <p>
 * During the write phase, instances are stored on disk via
 * {@link SpatialEntryStore} — all non-spatial data (objectId, featureType,
 * attributes) is stored in the {@link AttributeStore}. This reduces per-feature
 * heap usage to near zero, enabling 10M+ features without heap pressure.
 * <p>
 * During the close phase, entries are streamed back from disk for spatial
 * indexing (extent computation, grid partitioning, per-cell leaf build).
 */
public record SpatialEntry(long id, double centerX, double centerY, double[] bbox,
                           long meshHandle, long attrOffset) {
    /**
     * Fixed record size in bytes:
     * id(8) + centerX(8) + centerY(8) + bbox[6](48) + meshHandle(8) + attrOffset(8)
     */
    static final int RECORD_SIZE = 88;

    /** Encode this entry as one {@link #RECORD_SIZE}-byte record. */
    void writeTo(ByteBuffer buf) {
        buf.putLong(id);
        buf.putDouble(centerX);
        buf.putDouble(centerY);
        for (int i = 0; i < 6; i++) {
            buf.putDouble(bbox[i]);
        }
        buf.putLong(meshHandle);
        buf.putLong(attrOffset);
    }

    /** Decode one {@link #RECORD_SIZE}-byte record from the buffer's current position. */
    static SpatialEntry readFrom(ByteBuffer buf) {
        long id = buf.getLong();
        double centerX = buf.getDouble();
        double centerY = buf.getDouble();
        double[] bbox = new double[6];
        for (int i = 0; i < 6; i++) {
            bbox[i] = buf.getDouble();
        }
        long meshHandle = buf.getLong();
        long attrOffset = buf.getLong();
        return new SpatialEntry(id, centerX, centerY, bbox, meshHandle, attrOffset);
    }
}
