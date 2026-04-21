/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model;

/**
 * A named attribute field with its resolved type. Produced by
 * {@link org.citydb.vis.encoder.AttributeEncoder#finalizeFields} after
 * the write phase, consumed by the model layer for JSON schema generation
 * and by the encoder for binary attribute output.
 */
public record AttrField(String name, AttrType type) {
}
