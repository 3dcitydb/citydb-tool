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
import org.citydb.vis.appearance.AtlasFallbackStrategy;
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
    // Per-prototype texture atlas pages, built once on the close thread
    // by finalizePrototypes() BEFORE the parallel node fan-out — the build
    // remaps the shared prototype mesh's UVs in place, which must not race
    // with concurrent encoder reads. Plain map: written single-threaded,
    // read-only afterwards.
    private final Map<Integer, List<TextureAtlas>> atlasPagesById = new HashMap<>();
    // Largest per-axis instance scale seen per prototype during the write
    // phase. Frozen into weldToleranceById by finalizePrototypes() at the
    // same phase boundary where atlases freeze.
    private final ConcurrentHashMap<Integer, Double> maxInstanceScaleById = new ConcurrentHashMap<>();
    // Per-prototype weld tolerance in template-local units, computed once by
    // finalizePrototypes() so every node batch (and the encoder's
    // per-prototype geometry cache) sees one immutable value by construction.
    // Plain map: written single-threaded on the close thread, read-only
    // afterwards.
    private final Map<Integer, Float> weldToleranceById = new HashMap<>();
    // Per-prototype normalization scale (= largest per-instance scale),
    // frozen by finalizePrototypes() like the weld tolerance. The 3D Tiles
    // encoder bakes it into the prototype's vertex positions and divides
    // each instance's SCALE by it, so instance scales stay <= 1 and
    // CesiumJS's translation-only instancing bounds remain valid (see
    // CellFrame.scaled).
    private final Map<Integer, Double> normalizationScaleById = new HashMap<>();

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
                0L, TEMPLATE_SURFACE_TYPE, appearance.forTriangulation(),
                "implicit geometry template", true);
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
     * prototype determines the frozen weld and T-junction tolerances computed
     * by {@link #finalizePrototypes}. Called from the write phase only.
     */
    public void recordInstanceScale(int prototypeId, double[] scale) {
        double max = Math.max(scale[0], Math.max(scale[1], scale[2]));
        maxInstanceScaleById.merge(prototypeId, max, Math::max);
    }

    /**
     * The prototype's weld tolerance in template-local units, frozen by
     * {@link #finalizePrototypes}: the default tolerance divided by the
     * largest per-instance scale, so the effective world-space tolerance
     * never exceeds the default for any instance — tighter in template units
     * for scaled-up templates, looser for scaled-down ones.
     */
    public float weldTolerance(int prototypeId) {
        return weldToleranceById.getOrDefault(prototypeId, VertexWelder.DEFAULT_WELD_TOLERANCE);
    }

    /**
     * The prototype's normalization scale, frozen by
     * {@link #finalizePrototypes}: the largest per-instance scale (1 when no
     * instance was recorded). The 3D Tiles encoder scales the prototype's
     * encoded positions up by this factor and each instance's SCALE
     * attribute down by it — placement-equivalent, but it keeps every
     * instance scale at most 1 so CesiumJS's instancing culling bounds
     * (computed from POSITION extents + TRANSLATION range, ignoring
     * instance SCALE) contain the rendered geometry.
     */
    public double normalizationScale(int prototypeId) {
        return normalizationScaleById.getOrDefault(prototypeId, 1.0);
    }

    /**
     * Finalize the registered prototypes: freeze the scale-dependent weld
     * tolerance and normalization scale, run the deferred T-junction pass
     * (followed by the coplanar outline-edge merge when
     * {@code mergeCoplanarOutlineAngle} is non-null — the same post-build pass the
     * baked path applies in {@code FeatureProcessor}), and build the
     * per-prototype texture atlases. Prototypes without a recorded instance
     * scale (every instance fell back to baking) are skipped — their meshes
     * are never encoded. Must run on the close thread
     * after the write phase (texture BLOBs are batch-written by the DB
     * exporter and may not exist on disk earlier — see {@link TextureStore};
     * the largest per-instance scale is only known once every instance has
     * been planned) and before the parallel node fan-out: both the
     * T-junction pass and the atlas build mutate the shared prototype mesh
     * in place.
     * <p>
     * The T-junction pass cannot run at registration time: the template's
     * local units are arbitrary, so the metric snap tolerance is only
     * meaningful once the largest instance scale is known — applied as
     * {@code tolerance / maxScale} in template units, the same contract as
     * the weld tolerance. Registration-time resolution with the tolerance
     * misread as template units deformed dense templates (a ×30-scaled tree
     * gained 45% spurious surface at its trunk).
     * <p>
     * Prototype atlases obey the same quality contract as node atlases —
     * every texture is scaled by {@code --texture-scale} and no page exceeds
     * {@code --max-atlas-size}. The page-mode decision mirrors
     * {@code NodeAssembler}'s AUTO logic for the 3D Tiles writer (the only
     * writer with an instancing path): under {@code --atlas-fallback=expand} a
     * prototype whose textures overflow one page spills to multiple pages via
     * {@link TextureAtlasBuilder#buildMulti} (per-texture clamp at the page
     * cap) instead of expanding a single page past the user's cap; under
     * {@code rescale} the textures are shrunk into one capped page.
     * <p>
     * A prototype whose atlas cannot be built (every source texture failed to
     * load) is downgraded to untextured, mirroring the node-atlas fallback in
     * {@code NodeAssembler}.
     */
    public void finalizePrototypes(VisFormatOptions formatOptions, Double mergeCoplanarOutlineAngle) {
        for (Prototype prototype : byId.values()) {
            Double recordedScale = maxInstanceScaleById.get(prototype.id());
            if (recordedScale == null) {
                // Every instance of this prototype fell back to baking (e.g.
                // shear in the transformation matrix), so the mesh is never
                // encoded — and without a recorded scale the metric
                // tolerances below could not be expressed in template units
                // anyway. Leave it untouched.
                continue;
            }
            double maxScale = Math.max(recordedScale, 1e-6);
            weldToleranceById.put(prototype.id(),
                    (float) (VertexWelder.DEFAULT_WELD_TOLERANCE / maxScale));
            normalizationScaleById.put(prototype.id(), maxScale);

            TriangleMesh mesh = prototype.mesh();
            mesh.resolveTJunctions(1.0, 1.0,
                    GeometryMeshBuilder.T_JUNCTION_TOLERANCE_METERS / maxScale,
                    "prototype template " + prototype.id());
            mesh.removeDuplicateTriangles();
            if (mergeCoplanarOutlineAngle != null) {
                // Identity scales: template meshes are local Cartesian.
                mesh.mergeCoplanarOutlineEdges(1.0, 1.0, mergeCoplanarOutlineAngle);
            }
            Set<Integer> texIds = new LinkedHashSet<>();
            for (int texId : mesh.getTriangleTextureIds()) {
                if (texId >= 0) {
                    texIds.add(texId);
                }
            }
            if (texIds.isEmpty()) {
                continue;
            }
            List<TextureAtlas> pages;
            try {
                Map<Integer, float[]> uvExtents = mesh.computeUVExtents();
                boolean useMulti = formatOptions.getAtlasFallbackStrategy() == AtlasFallbackStrategy.EXPAND
                        && TextureAtlasBuilder.wouldOverflow(texIds, textureStore,
                                formatOptions.getTextureScale(),
                                formatOptions.getMaxAtlasSize(), uvExtents);
                if (useMulti) {
                    pages = TextureAtlasBuilder.buildMulti(texIds, textureStore,
                            formatOptions.getTextureScale(), formatOptions.getMaxAtlasSize(),
                            uvExtents);
                } else {
                    TextureAtlas single = TextureAtlasBuilder.build(texIds, textureStore,
                            formatOptions.getTextureScale(), formatOptions.getMaxAtlasSize(),
                            uvExtents, false,
                            formatOptions.getAtlasFallbackStrategy());
                    pages = single != null ? List.of(single) : List.of();
                }
            } catch (IOException e) {
                logger.warn("Failed to build the texture atlas of implicit-geometry " +
                        "template {}: {}", prototype.id(), e.getMessage());
                pages = List.of();
            }
            if (!pages.isEmpty()) {
                for (TextureAtlas page : pages) {
                    page.remapUVs(mesh);
                }
                atlasPagesById.put(prototype.id(), pages);
            } else {
                logger.warn("All textures of implicit-geometry template {} failed to load; " +
                        "its instances render untextured.", prototype.id());
                mesh.setHasTexCoords(false);
            }
        }
    }

    /**
     * The prototype's atlas pages built by {@link #finalizePrototypes}: one page in
     * the common fits-one-page case, several when the prototype's textures
     * overflow the {@code --max-atlas-size} cap under
     * {@code --atlas-fallback=expand}. Empty for untextured prototypes (and
     * atlas-build failures).
     */
    public List<TextureAtlas> atlases(int prototypeId) {
        return atlasPagesById.getOrDefault(prototypeId, List.of());
    }
}
