/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.writer;

import org.citydb.model.common.Matrix4x4;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.vis.appearance.AppearanceExtractor;
import org.citydb.vis.appearance.RingAppearance;
import org.citydb.vis.attribute.AttributeEncoder;
import org.citydb.vis.geometry.ImplicitInstanceTransformer;
import org.citydb.vis.geometry.RingAttributes;
import org.citydb.vis.geometry.TrsDecomposition;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.styling.ObjectStyleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plans the processing of one CityGML implicit-geometry instance: decides
 * between the GPU-instancing path (prototype + TRS placement payload) and the
 * baked fallback (world-space mesh copy), or skips the instance entirely
 * (pure reference, library object, or missing transformation/reference
 * point — a DEBUG line records each skip reason). Produces a {@link Plan}
 * describing what to process; the async dispatch itself stays with
 * {@link VisWriter}, so this class carries no threading concerns.
 * <p>
 * One planner is created per feature: instances of one feature share
 * objectId / featureType / attributes, and the planner's per-feature state
 * (appearance cache, attribute-blob offset) must not outlive the feature.
 */
final class ImplicitInstancePlanner {
    private static final Logger logger = LoggerFactory.getLogger(ImplicitInstancePlanner.class);

    /** Marker for the two plan flavours; consumers branch via instanceof. */
    interface Plan {
    }

    /**
     * GPU-instancing payload: the registered prototype plus this instance's
     * placement (matrix + decomposition + reference point) and resolved
     * style color. Processed via
     * {@code FeatureProcessor#processInstance}.
     */
    record InstancedPlan(long instanceId, String objectId, long attrOffset,
                         PrototypeRegistry.Prototype prototype,
                         Matrix4x4 transformationMatrix,
                         TrsDecomposition.Result trs, Point referencePoint,
                         float[] styleColor) implements Plan {
    }

    /**
     * Baked fallback: the prototype deep-copied and transformed into world
     * space, wrapped as a single {@link GeometryProperty}, with the
     * prototype's appearance remapped onto the copied rings. Processed via
     * the same {@code FeatureProcessor#process} path used for explicit
     * geometries.
     */
    record BakedPlan(long instanceId, GeometryProperty wrapped,
                     RingAttributes ringAttributes) implements Plan {
    }

    private final VisExportStores stores;
    private final AttributeEncoder attributeEncoder;
    private final ObjectStyleRegistry styleRegistry;
    private final PrototypeRegistry prototypeRegistry;
    private final AtomicLong featureIdCounter;
    private final boolean instancingSupported;

    // Shared per-feature context: every instance of the feature resolves to
    // the same CityGML feature for picking/attributes.
    private final String objectId;
    private final String featureType;
    private final String featureTypeNamespace;
    private final Map<String, Object> attributes;

    // Cache appearance extraction per prototype identity so a feature
    // with N instances of the same prototype walks the appearance
    // tree once. Scoped to this feature: implicit prototypes are
    // typically reused across features, but caching globally would
    // need a thread-safe map keyed by prototype identity — not worth
    // the complexity unless profiling shows a hot spot. Only consulted
    // on the baked fallback path; the instancing path caches appearance
    // globally inside the PrototypeRegistry.
    private final Map<ImplicitGeometry, RingAppearance> protoAppearanceCache = new IdentityHashMap<>();

    // Instances of one feature share objectId/featureType/attributes, so
    // the instanced path persists the attribute blob once per feature
    // (lazily, on the first successfully instanced occurrence) instead
    // of once per instance. Plain field: planning runs sequentially on
    // the caller thread.
    private long instanceAttrOffset = -1;

    ImplicitInstancePlanner(VisExportStores stores, AttributeEncoder attributeEncoder,
                            ObjectStyleRegistry styleRegistry, PrototypeRegistry prototypeRegistry,
                            AtomicLong featureIdCounter, boolean instancingSupported,
                            String objectId, String featureType, String featureTypeNamespace,
                            Map<String, Object> attributes) {
        this.stores = stores;
        this.attributeEncoder = attributeEncoder;
        this.styleRegistry = styleRegistry;
        this.prototypeRegistry = prototypeRegistry;
        this.featureIdCounter = featureIdCounter;
        this.instancingSupported = instancingSupported;
        this.objectId = objectId;
        this.featureType = featureType;
        this.featureTypeNamespace = featureTypeNamespace;
        this.attributes = attributes;
    }

    /**
     * Plan one implicit-geometry instance. Allocates the instance a fresh
     * featureId so spatial partitioning sees its own bbox, while the plan
     * shares the parent feature's objectId / featureType / attributes so
     * picking still resolves to the CityGML feature even though the spatial
     * entry is distinct.
     *
     * @return the plan, or {@code null} when the instance is silently skipped
     * @throws IOException when persisting the shared attribute blob fails
     */
    Plan plan(ImplicitGeometryProperty property) throws IOException {
        ImplicitGeometry implicitGeometry = property.getObject().orElse(null);
        if (implicitGeometry == null) {
            logger.debug("Skipping implicit-geometry-property without inline prototype on feature {}.",
                    objectId);
            return null;
        }
        Geometry<?> prototype = implicitGeometry.getGeometry().orElse(null);
        if (prototype == null) {
            // libraryObject form (.dae/.obj/...) — vis-export does not parse
            // external mesh files, would need a separate loader.
            logger.debug("Skipping library-object implicit geometry on feature {} (no inline mesh).",
                    objectId);
            return null;
        }
        Matrix4x4 transformationMatrix = property.getTransformationMatrix().orElse(null);
        Point referencePoint = property.getReferencePoint().orElse(null);
        if (transformationMatrix == null || referencePoint == null) {
            logger.debug("Skipping implicit instance on feature {} — missing transformation matrix or reference point.",
                    objectId);
            return null;
        }

        // GPU-instancing path: register the shared template once and plan
        // only the per-instance placement. Falls back to baking below when
        // the template is not instanceable or the matrix cannot be expressed
        // as rotation·scale (shear / mirroring).
        if (instancingSupported) {
            InstancedPlan instanced = planInstanced(implicitGeometry, transformationMatrix,
                    referencePoint);
            if (instanced != null) {
                return instanced;
            }
        }

        return planBaked(implicitGeometry, prototype, transformationMatrix, referencePoint);
    }

    /**
     * GPU-instancing plan: resolve the template through the
     * {@link PrototypeRegistry} and decompose the transformation matrix.
     * Returns {@code null} when the instance cannot be expressed as
     * prototype + TRS (caller falls back to the baked path).
     * <p>
     * The instance deliberately does <b>not</b> set the per-feature texture
     * flag even for textured prototypes: its textures live in the prototype's
     * own atlas and never contribute to the node atlas, so for the
     * mixed-texture and atlas-overflow split stages the instance counts as
     * untextured content.
     */
    private InstancedPlan planInstanced(ImplicitGeometry implicitGeometry,
                                        Matrix4x4 transformationMatrix,
                                        Point referencePoint) throws IOException {
        PrototypeRegistry.Prototype prototype =
                prototypeRegistry.getOrRegister(implicitGeometry);
        if (prototype == null) {
            return null;
        }
        TrsDecomposition.Result trs = TrsDecomposition.decompose(transformationMatrix);
        if (trs == null) {
            logger.debug("Baking implicit instance on feature {} — transformation matrix " +
                    "contains shear or mirroring.", objectId);
            return null;
        }
        prototypeRegistry.recordInstanceScale(prototype.id(), trs.scale());

        // Persist the shared attribute blob once per feature, on the first
        // successfully instanced occurrence (baked fallbacks store their own).
        if (instanceAttrOffset < 0) {
            instanceAttrOffset = stores.getAttrStore().store(objectId, featureType, attributes);
            attributeEncoder.trackFieldTypes(attributes);
        }

        // Resolve the per-feature-type style while the qualified type name is
        // still at hand (the attribute store keeps only the local name) and
        // capture its color in the instance payload.
        float[] styleColor = styleRegistry
                .resolve(Name.of(featureType, featureTypeNamespace)).color();

        long instanceId = featureIdCounter.incrementAndGet();
        return new InstancedPlan(instanceId, objectId, instanceAttrOffset, prototype,
                transformationMatrix, trs, referencePoint, styleColor);
    }

    /**
     * Baked fallback: materialize the instance into a world-space mesh via a
     * deep-copy transform of the prototype and remap the prototype's
     * appearance onto the copied rings.
     */
    private BakedPlan planBaked(ImplicitGeometry implicitGeometry, Geometry<?> prototype,
                                Matrix4x4 transformationMatrix, Point referencePoint) {
        RingAppearance protoAppearance = protoAppearanceCache.computeIfAbsent(
                implicitGeometry,
                p -> AppearanceExtractor.extract(p, stores.getTextureStore()));

        ImplicitInstanceTransformer.Result placed = ImplicitInstanceTransformer.transform(
                prototype, transformationMatrix, referencePoint);
        RingAppearance instanceAppearance = protoAppearance.remapKeys(placed.ringMap());

        long instanceId = featureIdCounter.incrementAndGet();
        Map<LinearRing, Integer> instanceTextureIds = instanceAppearance.ringTextureIds();
        if (instanceTextureIds != null && !instanceTextureIds.isEmpty()) {
            stores.setFeatureTextured(instanceId);
        }

        // Wrap the placed prototype copy as a single GeometryProperty. Its
        // parent walk does not reach a Feature (the copy is a fresh tree
        // detached from the feature graph), so GeometryMeshBuilder falls
        // back to defaultSurfaceType — exactly what we want: every triangle
        // gets the parent feature's type for styling purposes.
        GeometryProperty wrapped = GeometryProperty.of(
                Name.of("implicitInstance", Namespaces.CORE),
                placed.geometry());

        return new BakedPlan(instanceId, wrapped, instanceAppearance.forTriangulation());
    }
}
