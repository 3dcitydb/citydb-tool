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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ImplicitGeometryRegistry {
    private final ImplicitGeometryScope scope;
    private final Map<Long, String> objectIds = new ConcurrentHashMap<>();
    private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();
    private final Map<Long, ImplicitGeometryDescriptor> descriptors = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> pending = new ConcurrentHashMap<>();

    private ImplicitGeometryRegistry(ImplicitGeometryScope scope) {
        this.scope = scope != null ? scope : ImplicitGeometryScope.GLOBAL;
    }

    public static ImplicitGeometryRegistry of(ImplicitGeometryScope scope) {
        return new ImplicitGeometryRegistry(scope);
    }

    public String getObjectId(Long id) {
        return id != null ? objectIds.get(id) : null;
    }

    public Envelope getEnvelope(String objectId) {
        Envelope envelope = objectId != null ? envelopes.get(objectId) : null;
        return envelope != null ? envelope.copy() : null;
    }

    public ImplicitGeometryDescriptor getDescriptor(Long id) {
        return id != null ? descriptors.get(id) : null;
    }

    public Set<Long> claim(Set<Long> ids) {
        if (scope == ImplicitGeometryScope.GLOBAL) {
            return ids.stream()
                    .filter(id -> !descriptors.containsKey(id))
                    .filter(id -> pending.putIfAbsent(id, new CompletableFuture<>()) == null)
                    .collect(Collectors.collectingAndThen(Collectors.toSet(), Set::copyOf));
        } else {
            return ids;
        }
    }

    public void fail(Set<Long> ids, Throwable cause) {
        ids.forEach(id -> fail(id, cause));
    }

    public Map<Long, ImplicitGeometry> resolve(Set<Long> implicitGeometryIds, Set<Long> claimedIds, ImplicitGeometryLoader loader) throws ExportException, SQLException {
        return scope == ImplicitGeometryScope.TOP_LEVEL_FEATURE
                ? loader.load(implicitGeometryIds)
                : resolveGlobal(implicitGeometryIds, claimedIds, loader);
    }

    private Map<Long, ImplicitGeometry> resolveGlobal(Set<Long> implicitGeometryIds, Set<Long> claimedIds, ImplicitGeometryLoader loader) throws ExportException, SQLException {
        try {
            Map<Long, ImplicitGeometry> implicitGeometries = !claimedIds.isEmpty()
                    ? loader.load(claimedIds)
                    : Map.of();
            for (Map.Entry<Long, ImplicitGeometry> entry : implicitGeometries.entrySet()) {
                registerMetadata(entry.getKey(), entry.getValue());
            }

            claimedIds.forEach(this::complete);
            implicitGeometryIds.stream()
                    .filter(id -> !claimedIds.contains(id))
                    .filter(id -> !descriptors.containsKey(id))
                    .map(pending::get)
                    .filter(Objects::nonNull)
                    .forEach(CompletableFuture::join);

            return implicitGeometries;
        } catch (Exception e) {
            claimedIds.forEach(id -> fail(id, e));
            throw e;
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
        objectIds.put(id, objectId);
        descriptors.put(id, ImplicitGeometryDescriptor.of(implicitGeometry));
        implicitGeometry.getGeometry().ifPresent(geometry -> envelopes.put(objectId, geometry.getEnvelope().copy()));
    }

    public void clear() {
        objectIds.clear();
        envelopes.clear();
        descriptors.clear();
        pending.clear();
    }

    @FunctionalInterface
    public interface ImplicitGeometryLoader {
        Map<Long, ImplicitGeometry> load(Set<Long> ids) throws ExportException, SQLException;
    }
}
