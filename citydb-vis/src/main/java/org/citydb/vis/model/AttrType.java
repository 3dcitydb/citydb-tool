/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

/**
 * Attribute value type. Drives binary encoding layout and metadata schema
 * generation in visualization export formats (I3S, 3D Tiles).
 */
public enum AttrType {
    STRING, INT, DOUBLE, OID
}
