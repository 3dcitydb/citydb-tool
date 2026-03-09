/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.srs;

import org.citydb.core.concurrent.LazyInitializer;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;

import java.util.Objects;
import java.util.Optional;

public class SpatialReference {
    private final int srid;
    private final SpatialReferenceType type;
    private final String name;
    private final String identifier;
    private final String wkt;
    private final LazyInitializer<CoordinateReferenceSystem> definition;

    private SpatialReference(int srid, SpatialReferenceType type, String name, String identifier, String wkt) {
        this.srid = srid;
        this.type = Objects.requireNonNullElse(type, SpatialReferenceType.UNKNOWN_CRS);
        this.name = Objects.requireNonNullElse(name, "n/a");
        this.identifier = Objects.requireNonNullElse(identifier, "http://www.opengis.net/def/crs/EPSG/0/" + srid);
        this.wkt = Objects.requireNonNullElse(wkt, "");
        definition = LazyInitializer.of(this::buildDefinition);
    }

    public static SpatialReference of(int srid, SpatialReferenceType type, String name, String identifier, String wkt) {
        return new SpatialReference(srid, type, name, identifier, wkt);
    }

    public int getSRID() {
        return srid;
    }

    public SpatialReferenceType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getWKT() {
        return wkt;
    }

    public Optional<CoordinateReferenceSystem> getDefinition() {
        return Optional.ofNullable(definition.get());
    }

    public int getDimension() {
        if (definition.get() != null) {
            return definition.get().getCoordinateSystem().getDimension();
        } else {
            return type == SpatialReferenceType.COMPOUND_CRS
                    || type == SpatialReferenceType.GEOGRAPHIC3D_CRS ?
                    3 : 2;
        }
    }

    private CoordinateReferenceSystem buildDefinition() {
        CoordinateReferenceSystem crs = null;
        try {
            crs = CRS.parseWKT(wkt);
        } catch (Exception e) {
            //
        }

        if (crs == null) {
            try {
                crs = CRS.decode("EPSG:" + srid);
            } catch (Exception e) {
                //
            }
        }

        if (crs == null) {
            try {
                crs = CRS.decode(identifier);
            } catch (Exception e) {
                //
            }
        }

        return crs;
    }
}
