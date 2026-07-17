/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.GeometryProperty;
import org.citydb.vis.appearance.AppearanceExtractor;
import org.citydb.vis.appearance.RingAppearance;
import org.citydb.vis.appearance.TextureAtlas;
import org.citydb.vis.appearance.TextureAtlasBuilder;
import org.citydb.vis.config.VisFormatOptions;
import org.citydb.vis.geometry.GeometryMeshBuilder;
import org.citydb.vis.geometry.ImplicitInstanceTransformer;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.store.TextureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-once registry of implicit-geometry templates for the GPU-instancing
 * path. Each distinct prototype is triangulated a single time in its own
 * <b>local Cartesian meter</b> frame (no degree conversion — the placement
 * happens per instance via TRS attributes at encode time), and every
 * instance thereafter only references the registered prototype by id.
 * <p>
 * Keyed by the template's objectId (the IMPLICIT_GEOMETRY row's gml:id, which
 * the DB exporter attaches to every template), with an identity-keyed fallback
 * for templates without one. A registered {@code Optional.empty()} caches the
 * "not instanceable" verdict (no inline mesh / empty triangulation) so the
 * per-instance fallback decision is made once per template, not per instance.
 * <p>
 * The prototype mesh's per-triangle featureId and surface type are
 * placeholders: instanced rendering assigns feature identity per instance
 * (glTF {@code EXT_instance_features}) and styling per instance group, so
 * neither per-triangle lane is consulted on the instancing path.
 */
public class PrototypeRegistry {
    private static final Name TEMPLATE_SURFACE_TYPE = Name.of("ImplicitGeometry", Namespaces.CORE);
    private final Logger logger = LoggerFactory.getLogger(PrototypeRegistry.class);

    private final TextureStore textureStore;
    private final ConcurrentHashMap<String, Optional<Prototype>> byObjectId = new ConcurrentHashMap<>();
    private final Map<ImplicitGeometry, Optional<Prototype>> byIdentity =
            Collections.synchronizedMap(new IdentityHashMap<>());
    private final ConcurrentHashMap<Integer, Prototype> byId = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger();
    // Per-prototype single-page texture atlas, built once on the close thread
    // by buildAtlases() BEFORE the parallel node fan-out — the build remaps
    // the shared prototype mesh's UVs in place, which must not race with
    // concurrent encoder reads. Plain map: written single-threaded, read-only
    // afterwards.
    private final Map<Integer, TextureAtlas> atlasById = new HashMap<>();
    // Largest per-axis instance scale seen per prototype during the write
    // phase. Frozen into weldToleranceById by buildAtlases() at the same
    // phase boundary where atlases freeze.
    private final ConcurrentHashMap<Integer, Double> maxInstanceScaleById = new ConcurrentHashMap<>();
    // Per-prototype weld tolerance in template-local units, computed once by
    // buildAtlases() so every node batch (and the encoder's per-prototype
    // geometry cache) sees one immutable value by construction. Plain map:
    // written single-threaded on the close thread, read-only afterwards.
    private final Map<Integer, Float> weldToleranceById = new HashMap<>();

    /**
     * @param id        registry-assigned prototype id, dense from 0
     * @param mesh      triangulated template in local Cartesian meters
     * @param localAabb local-frame bounding box {@code [minX, minY, minZ, maxX, maxY, maxZ]}
     */
    public record Prototype(int id, TriangleMesh mesh, double[] localAabb) {
    }

    public PrototypeRegistry(TextureStore textureStore) {
        this.textureStore = textureStore;
    }

    /**
     * Return the registered prototype for the given template, processing it
     * on first encounter. Returns {@code null} when the template cannot be
     * instanced (no inline mesh, or triangulation produced nothing) — the
     * caller then falls back to the baked per-instance pipeline.
     */
    public Prototype getOrRegister(ImplicitGeometry implicitGeometry) {
        String objectId = implicitGeometry.getObjectId().orElse(null);
        Optional<Prototype> prototype = objectId != null
                ? byObjectId.computeIfAbsent(objectId, key -> process(implicitGeometry))
                : byIdentity.computeIfAbsent(implicitGeometry, this::process);
        return prototype.orElse(null);
    }

    /** Look up a registered prototype by its registry id. */
    public Prototype get(int prototypeId) {
        return byId.get(prototypeId);
    }

    /** Number of registered (instanceable) prototypes. */
    public int size() {
        return byId.size();
    }

    private Optional<Prototype> process(ImplicitGeometry implicitGeometry) {
        Geometry<?> geometry = implicitGeometry.getGeometry().orElse(null);
        if (geometry == null) {
            return Optional.empty();
        }

        // Deep-copy before wrapping: the template object may be shared and
        // must not be re-parented into a fresh GeometryProperty.
        ImplicitInstanceTransformer.Result copied = ImplicitInstanceTransformer.copy(geometry);
        RingAppearance appearance = AppearanceExtractor.extract(implicitGeometry, textureStore)
                .remapKeys(copied.ringMap());

        TriangleMesh mesh = GeometryMeshBuilder.build(
                List.of(GeometryProperty.of(Name.of("implicitTemplate", Namespaces.CORE),
                        copied.geometry())),
                0L, TEMPLATE_SURFACE_TYPE, appearance.forTriangulation(), true);
        if (mesh.isEmpty()) {
            return Optional.empty();
        }

        Prototype prototype = new Prototype(idCounter.getAndIncrement(), mesh,
                mesh.computeBoundingBox());
        byId.put(prototype.id(), prototype);
        return Optional.of(prototype);
    }

    /**
     * Record one instance's per-axis scale. The largest recorded scale per
     * prototype determines the frozen weld tolerance computed by
     * {@link #buildAtlases}. Called from the write phase only.
     */
    public void recordInstanceScale(int prototypeId, double[] scale) {
        double max = Math.max(scale[0], Math.max(scale[1], scale[2]));
        maxInstanceScaleById.merge(prototypeId, max, Math::max);
    }

    /**
     * The prototype's weld tolerance in template-local units, frozen by
     * {@link #buildAtlases}: the default tolerance divided by the largest
     * per-instance scale, so the effective world-space tolerance never
     * exceeds the default for any instance — tighter in template units for
     * scaled-up templates, looser for scaled-down ones.
     */
    public float weldTolerance(int prototypeId) {
        return weldToleranceById.getOrDefault(prototypeId, VertexWelder.DEFAULT_WELD_TOLERANCE);
    }

    /**
     * Build the per-prototype texture atlases. Must run on the close thread
     * after the write phase (texture BLOBs are batch-written by the DB
     * exporter and may not exist on disk earlier — see {@link TextureStore})
     * and before the parallel node fan-out: the build rewrites the shared
     * prototype mesh's UVs into atlas space in place.
     * <p>
     * A prototype whose atlas cannot be built (every source texture failed to
     * load) is downgraded to untextured, mirroring the node-atlas fallback in
     * {@code NodeAssembler}.
     */
    public void buildAtlases(VisFormatOptions formatOptions) {
        for (Prototype prototype : byId.values()) {
            weldToleranceById.put(prototype.id(), (float) (VertexWelder.DEFAULT_WELD_TOLERANCE
                    / Math.max(maxInstanceScaleById.getOrDefault(prototype.id(), 1.0), 1e-6)));

            TriangleMesh mesh = prototype.mesh();
            Set<Integer> texIds = new LinkedHashSet<>();
            for (int texId : mesh.getTriangleTextureIds()) {
                if (texId >= 0) {
                    texIds.add(texId);
                }
            }
            if (texIds.isEmpty()) {
                continue;
            }
            TextureAtlas atlas;
            try {
                atlas = TextureAtlasBuilder.build(texIds, textureStore,
                        formatOptions.getTextureScale(), formatOptions.getMaxAtlasSize(),
                        mesh.computeUVExtents(), false,
                        formatOptions.getAtlasFallbackStrategy());
            } catch (IOException e) {
                logger.warn("Failed to build the texture atlas of implicit-geometry " +
                        "template {}: {}", prototype.id(), e.getMessage());
                atlas = null;
            }
            if (atlas != null) {
                atlas.remapUVs(mesh);
                atlasById.put(prototype.id(), atlas);
            } else {
                logger.warn("All textures of implicit-geometry template {} failed to load; " +
                        "its instances render untextured.", prototype.id());
                mesh.setHasTexCoords(false);
            }
        }
    }

    /**
     * The prototype's atlas built by {@link #buildAtlases}, or {@code null}
     * for untextured prototypes (and atlas-build failures).
     */
    public TextureAtlas atlas(int prototypeId) {
        return atlasById.get(prototypeId);
    }
}
