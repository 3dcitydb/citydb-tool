/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

/**
 * A named attribute field with its resolved type. Produced by
 * {@link org.citydb.vis.encoder.I3SAttributeEncoder#finalizeFields} after
 * the write phase, consumed by the model layer for JSON schema generation
 * and by the encoder for binary attribute output.
 */
public record AttrField(String name, AttrType type) {
}
