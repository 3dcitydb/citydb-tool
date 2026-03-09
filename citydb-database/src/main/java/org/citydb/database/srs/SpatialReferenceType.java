/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.srs;

public enum SpatialReferenceType {
    PROJECTED_CRS,
    GEOGRAPHIC_CRS,
    GEOGRAPHIC3D_CRS,
    GEODETIC_CRS,
    GEOCENTRIC_CRS,
    COMPOUND_CRS,
    ENGINEERING_CRS,
    UNKNOWN_CRS
}
