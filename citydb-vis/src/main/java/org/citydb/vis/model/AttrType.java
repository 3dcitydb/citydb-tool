/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model;

/**
 * Attribute value type. Drives binary encoding layout and metadata schema
 * generation in visualization export formats (I3S, 3D Tiles).
 */
public enum AttrType {
    STRING, INT, DOUBLE, OID
}
