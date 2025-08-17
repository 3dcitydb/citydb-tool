/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.cli.importer.filter;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.srs.SrsException;
import org.citydb.io.reader.filter.FilterException;
import org.citydb.io.reader.filter.FilterPredicate;
import org.citydb.io.reader.options.BboxMode;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Envelope;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BboxPredicate implements FilterPredicate {
    private final Envelope bbox;
    private final BboxMode mode;
    private final DatabaseAdapter adapter;
    private final int srid;
    private final Map<Integer, Envelope> bboxes = new ConcurrentHashMap<>();

    private Exception exception;

    private BboxPredicate(Envelope bbox, BboxMode mode, DatabaseAdapter adapter) {
        this.bbox = bbox;
        this.mode = mode;
        this.adapter = adapter;

        if (bbox.getSRID().isPresent()) {
            srid = bbox.getSRID().get();
        } else {
            srid = adapter.getDatabaseMetadata().getSpatialReference().getSRID();
            bbox.setSRID(srid);
        }
    }

    static BboxPredicate of(Envelope bbox, BboxMode mode, DatabaseAdapter adapter) {
        return new BboxPredicate(bbox, mode, adapter);
    }

    @Override
    public boolean test(Feature feature) throws FilterException {
        Envelope envelope = feature.getEnvelope().orElse(null);
        if (envelope != null) {
            Envelope bbox = getOrTransformBbox(envelope);
            return switch (mode) {
                case INTERSECTS -> bbox.intersects(envelope);
                case CONTAINS -> bbox.contains(envelope);
                case ON_TILE -> bbox.isOnTile(envelope);
            };
        } else {
            return false;
        }
    }

    private Envelope getOrTransformBbox(Envelope envelope) throws FilterException {
        int targetSRID = getTargetSRID(envelope);
        if (srid != targetSRID) {
            Envelope bbox = bboxes.computeIfAbsent(targetSRID, k -> {
                try {
                    return adapter.getGeometryAdapter().transform(this.bbox, targetSRID);
                } catch (Exception e) {
                    exception = e;
                    return null;
                }
            });

            if (exception != null) {
                throw new FilterException("Failed to transform the bounding box filter to the feature SRS.", exception);
            } else {
                return bbox;
            }
        } else {
            return bbox;
        }
    }

    private int getTargetSRID(Envelope envelope) {
        try {
            return adapter.getGeometryAdapter().getSpatialReference(envelope)
                    .orElse(adapter.getDatabaseMetadata().getSpatialReference())
                    .getSRID();
        } catch (SrsException | SQLException e) {
            return adapter.getDatabaseMetadata().getSpatialReference().getSRID();
        }
    }
}
