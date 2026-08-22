/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.ImplicitGeometryDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.options.ImplicitGeometryScope;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ImplicitGeometryRegistry {
    private final ImplicitGeometryScope scope;
    private final Map<Long, CompletableFuture<Metadata>> metadata = new ConcurrentHashMap<>();
    private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();

    public record Metadata(String objectId, ImplicitGeometryDescriptor descriptor) {
    }

    private ImplicitGeometryRegistry(ImplicitGeometryScope scope) {
        this.scope = scope != null ? scope : ImplicitGeometryScope.GLOBAL;
    }

    public static ImplicitGeometryRegistry of(ImplicitGeometryScope scope) {
        return new ImplicitGeometryRegistry(scope);
    }

    public Metadata getMetadata(Long id) {
        if (id != null) {
            CompletableFuture<Metadata> future = metadata.get(id);
            return future != null ? future.join() : null;
        }

        return null;
    }

    public Envelope getEnvelope(String objectId) {
        Envelope envelope = objectId != null ? envelopes.get(objectId) : null;
        return envelope != null ? envelope.copy() : null;
    }

    public Set<Long> claim(Set<Long> ids) {
        if (scope == ImplicitGeometryScope.GLOBAL) {
            return ids.stream()
                    .filter(id -> metadata.putIfAbsent(id, new CompletableFuture<>()) == null)
                    .collect(Collectors.collectingAndThen(Collectors.toSet(), Set::copyOf));
        } else {
            return ids;
        }
    }

    public void fail(Set<Long> ids, Throwable cause) {
        ids.forEach(id -> fail(id, cause));
    }

    public Map<Long, ImplicitGeometry> resolve(Set<Long> claimedIds, ImplicitGeometryLoader loader) throws ExportException, SQLException {
        return scope == ImplicitGeometryScope.TOP_LEVEL_FEATURE
                ? loader.load(claimedIds)
                : resolveGlobal(claimedIds, loader);
    }

    private Map<Long, ImplicitGeometry> resolveGlobal(Set<Long> claimedIds, ImplicitGeometryLoader loader) throws ExportException, SQLException {
        try {
            Map<Long, ImplicitGeometry> implicitGeometries = !claimedIds.isEmpty()
                    ? loader.load(claimedIds)
                    : Map.of();
            for (Map.Entry<Long, ImplicitGeometry> entry : implicitGeometries.entrySet()) {
                complete(entry.getKey(), registerMetadata(entry.getValue()));
            }

            claimedIds.stream()
                    .filter(id -> !implicitGeometries.containsKey(id))
                    .forEach(id -> complete(id, null));

            return implicitGeometries;
        } catch (Exception e) {
            claimedIds.forEach(id -> fail(id, e));
            throw e;
        }
    }

    private void complete(Long id, Metadata metadata) {
        CompletableFuture<Metadata> future = this.metadata.get(id);
        if (future != null) {
            future.complete(metadata);
        }
    }

    private void fail(Long id, Throwable cause) {
        CompletableFuture<Metadata> future = metadata.get(id);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    private Metadata registerMetadata(ImplicitGeometry implicitGeometry) {
        String objectId = implicitGeometry.getOrCreateObjectId();
        implicitGeometry.getGeometry().ifPresent(geometry -> envelopes.put(objectId, geometry.getEnvelope().copy()));
        return new Metadata(objectId, ImplicitGeometryDescriptor.of(implicitGeometry));
    }

    public void clear() {
        envelopes.clear();
        metadata.clear();
    }

    @FunctionalInterface
    public interface ImplicitGeometryLoader {
        Map<Long, ImplicitGeometry> load(Set<Long> ids) throws ExportException, SQLException;
    }
}
