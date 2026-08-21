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
    private final Map<Long, Metadata> metadata = new ConcurrentHashMap<>();
    private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

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
            await(id);
            return metadata.get(id);
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
                    .filter(id -> !metadata.containsKey(id))
                    .filter(id -> pending.putIfAbsent(id, new CompletableFuture<>()) == null)
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
                registerMetadata(entry.getKey(), entry.getValue());
            }

            claimedIds.forEach(this::complete);
            return implicitGeometries;
        } catch (Exception e) {
            claimedIds.forEach(id -> fail(id, e));
            throw e;
        }
    }

    private void await(Long id) {
        CompletableFuture<Void> future = pending.get(id);
        if (future != null) {
            future.join();
        }
    }

    private void complete(Long id) {
        CompletableFuture<Void> future = pending.get(id);
        if (future != null) {
            future.complete(null);
        }
    }

    private void fail(Long id, Throwable cause) {
        CompletableFuture<Void> future = pending.get(id);
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

    private void registerMetadata(Long id, ImplicitGeometry implicitGeometry) {
        String objectId = implicitGeometry.getOrCreateObjectId();
        metadata.put(id, new Metadata(objectId, ImplicitGeometryDescriptor.of(implicitGeometry)));
        implicitGeometry.getGeometry().ifPresent(geometry -> envelopes.put(objectId, geometry.getEnvelope().copy()));
    }

    public void clear() {
        metadata.clear();
        envelopes.clear();
        pending.clear();
    }

    @FunctionalInterface
    public interface ImplicitGeometryLoader {
        Map<Long, ImplicitGeometry> load(Set<Long> ids) throws ExportException, SQLException;
    }
}
