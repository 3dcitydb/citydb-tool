/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.encoder.TriangleRouter;
import org.citydb.vis.encoder.TriangleRouter.RoutedTriangle;
import org.citydb.vis.geometry.TriangleMesh;
import org.citydb.vis.geometry.VertexWelder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.FeatureData;
import org.citydb.vis.model.InstanceBatch;
import org.citydb.vis.scene.BoundingVolume;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.styling.DefaultObjectStyle;
import org.citydb.vis.styling.ObjectStyleRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes a scene node's geometry, textures, and per-feature metadata into a
 * glTF 2.0 Binary (GLB) file without external library dependencies.
 * <p>
 * Each GLB contains:
 * <ul>
 *   <li>Mesh geometry: positions (local ENU), normals or UVs, indices</li>
 *   <li>Textures: one JPEG atlas per page embedded as buffer views</li>
 *   <li>{@code EXT_mesh_features}: per-vertex feature ID attribute</li>
 *   <li>{@code EXT_structural_metadata}: per-feature property table</li>
 * </ul>
 * <p>
 * A node emits up to three flavours of primitives, all sharing the same
 * mesh / property table. {@code --enable-shading} toggles NORMAL emission
 * and the {@code KHR_materials_unlit} extension uniformly across every
 * primitive type, mirroring the I3S writer:
 * <ul>
 *   <li>Textured primitive (one per atlas page): {@code POSITION +
 *       TEXCOORD_0 + _FEATURE_ID_0}, plus {@code NORMAL} when
 *       {@code --enable-shading} is on. The emitted normal is the local
 *       "up" direction (+Y in GLB Y-up local frame, which the per-cell
 *       tile transform rotates to ECEF up at the cell center) — not the
 *       polygon's true geometric normal. Lambertian then evaluates against
 *       a single direction across all textured triangles in the node, so
 *       walls and roofs end up at the same brightness (matches the I3S
 *       up-normal trick). When the flag is off the material carries
 *       {@code KHR_materials_unlit} to prevent CesiumJS from auto-deriving
 *       flat normals (which would dim the texture).</li>
 *   <li>Untextured-plain primitive (one per distinct {@link DefaultObjectStyle}
 *       used by the node): {@code POSITION + _FEATURE_ID_0}, plus
 *       {@code NORMAL} when {@code --enable-shading} is on. The material
 *       carries the style's {@code baseColorFactor} and switches between
 *       PBR-shaded and {@code KHR_materials_unlit} based on the flag.
 *       Per-feature-type overrides reach the GLB through this split —
 *       e.g. RoofSurface red + WallSurface white emits two plain primitives
 *       in one node.</li>
 *   <li>Untextured-colored primitive (X3DMaterial): {@code POSITION +
 *       COLOR_0 + _FEATURE_ID_0}, plus {@code NORMAL} when
 *       {@code --enable-shading} is on. Its material is PBR when shaded
 *       and unlit otherwise.</li>
 * </ul>
 * Positions are in a local ENU coordinate frame relative to the node's cell
 * center. The cell root's per-cell {@code transform} in the tileset converts
 * that cell-local ENU to global ECEF, re-establishing the tangent plane at
 * every cell so Earth curvature does not lift distant cells off the ground.
 * <p>
 * This class orchestrates the node encode flow; the heavy lifting lives in
 * package-private companions — {@link GlbPrimitiveBuilder} (triangle subset →
 * vertex arrays → BIN buffer views), {@link InstancedNodeEncoder} (GPU
 * instancing with the per-prototype geometry cache), and
 * {@link PropertyTableEncoder} (property-table columns).
 */
public class GlbEncoder {
    // Prototype geometry cache lives inside; writer-lifetime, thread-safe for
    // the parallel node fan-out.
    private final InstancedNodeEncoder instancedNodeEncoder = new InstancedNodeEncoder();

    /**
     * Encode a mesh node into a GLB byte array. Returns {@code null} if the
     * mesh is empty after welding/degenerate filtering.
     *
     * @param node           scene node (mesh will be cleared after encoding)
     * @param atlasBytesList JPEG bytes per atlas page (index matches
     *                       {@code texIdToPage} values), empty if untextured
     * @param texIdToPage    texture id → atlas page index; empty if untextured
     * @param features       per-feature attribute data
     * @param attrFields     finalized attribute field definitions
     * @param cellCenter     [centerLon, centerLat, centerAlt] of this node's
     *                       cell (the anchor of the cell root's per-cell
     *                       ENU-to-ECEF tile transform). All positions are
     *                       encoded relative to this point, so it must match
     *                       the anchor used to build the tile's transform.
     * @param styleRegistry  per-feature-type style registry. Each plain
     *                       triangle is bucketed by the style its source
     *                       surface type (recorded per-triangle on the
     *                       mesh) resolves to, producing one plain
     *                       primitive (and one plain material) per
     *                       distinct style used by the node. Falls back
     *                       to the registry's default style for types
     *                       without an override.
     * @return GLB bytes, or {@code null} if empty
     */
    public byte[] encode(SceneNode node, List<byte[]> atlasBytesList,
                         Map<Integer, Integer> texIdToPage,
                         List<FeatureData> features, List<AttrField> attrFields,
                         List<InstanceBatch> instanceBatches,
                         List<byte[]> instanceAtlasBytes,
                         double[] cellCenter,
                         ObjectStyleRegistry styleRegistry,
                         boolean enableShading) throws IOException {
        TriangleMesh mesh = node.getMesh();
        boolean hasInstances = instanceBatches != null && !instanceBatches.isEmpty();
        boolean hasTexture = mesh.hasTexCoords() && !atlasBytesList.isEmpty();
        if (!hasTexture) {
            node.setTextured(false);
        }

        BoundingVolume mbs = node.getBoundingVolume();
        VertexWelder.WeldResult weld = VertexWelder.weldAndFilter(mesh,
                mbs.getCenterX(), mbs.getCenterY(), mbs.getCenterZ());
        node.setOutputVertexCount(weld.vertexCount());
        if (weld.isEmpty() && !hasInstances) {
            node.setMesh(null);
            return null;
        }

        CellFrame frame = CellFrame.from(mbs, cellCenter);

        int pageCount = atlasBytesList.size();
        List<GlbPrimitiveBuilder.PrimitiveArrays> primitives = new ArrayList<>();
        int featureCount = 0;
        if (!weld.isEmpty()) {
            primitives = buildMainPrimitives(mesh, weld, texIdToPage, pageCount,
                    hasTexture, frame, styleRegistry, enableShading);
            featureCount = weld.faceRanges().size();
        }
        node.setMesh(null); // release source mesh; no longer needed

        // Property-table rows: the mesh's face ranges first, then one row per
        // instance (appended by InstancedNodeEncoder.build in emission order,
        // so the per-instance _FEATURE_ID_0 values line up).
        List<FeatureData> propFeatures = featureCount < features.size()
                ? FeatureData.reorderByIds(features, weld.rangeFeatureIds())
                : features;
        List<InstancedNodeEncoder.InstancedNodeData> instancedNodes = List.of();
        if (hasInstances) {
            propFeatures = new ArrayList<>(propFeatures);
            instancedNodes = instancedNodeEncoder.build(instanceBatches, instanceAtlasBytes,
                    cellCenter, styleRegistry, enableShading, propFeatures);
            if (primitives.isEmpty() && instancedNodes.isEmpty()) {
                return null;
            }
        }

        BinBufferBuilder bin = new BinBufferBuilder();
        List<GlbPrimitiveBuilder.PrimitiveBufferIds> primitiveBvs = new ArrayList<>(primitives.size());
        for (GlbPrimitiveBuilder.PrimitiveArrays p : primitives) {
            primitiveBvs.add(GlbPrimitiveBuilder.writeBuffers(bin, p));
        }
        // Embed each atlas page that is actually referenced by a primitive.
        // Pages whose triangles were all filtered out contribute nothing.
        List<Integer> bvTextures = new ArrayList<>(pageCount);
        boolean[] pageInUse = new boolean[pageCount];
        for (GlbPrimitiveBuilder.PrimitiveArrays p : primitives) {
            if (p.atlasPage() >= 0) pageInUse[p.atlasPage()] = true;
        }
        for (int p = 0; p < pageCount; p++) {
            bvTextures.add(pageInUse[p] ? bin.addRawBytes(atlasBytesList.get(p)) : -1);
        }

        List<GltfJsonBuilder.InstancedNode> jsonInstancedNodes =
                InstancedNodeEncoder.writeBuffers(instancedNodes, bin);

        List<PropertyTableBufferViews> propBvs = new ArrayList<>();
        for (AttrField field : attrFields) {
            propBvs.add(PropertyTableEncoder.encode(bin, field, propFeatures));
        }

        byte[] binData = bin.toByteArray();
        byte[] jsonData = new GltfJsonBuilder()
                .bufferViews(bin.getBufferViews(), binData.length)
                .textures(bvTextures)
                .primitives(toJsonPrimitives(primitives, primitiveBvs))
                .instancedNodes(jsonInstancedNodes)
                .metadata(propFeatures.size(), attrFields, propBvs)
                .enableShading(enableShading)
                .build();

        return GlbContainer.assemble(jsonData, binData);
    }

    /**
     * Classify valid triangles into format-neutral routing facts once
     * (texture id, X3DMaterial-colored flag, resolved style, face-range
     * row), then bucket them into the GLB's primitive flavours: one
     * textured primitive per atlas page, one "plain" primitive per
     * distinct {@link DefaultObjectStyle} (PBR-shaded, default baseColor),
     * and one "colored" primitive (X3DMaterial vertex colors, unlit). A
     * CityGML 3.0 Building with mixed RoofSurface / WallSurface /
     * GroundSurface boundaries therefore emits one plain primitive (and one
     * material) per distinct style used. The face-range index carried on
     * each routed triangle lets the per-vertex {@code _FEATURE_ID_0}
     * reference the shared property table regardless of which primitive the
     * triangle lands in — two non-contiguous runs of the same feature become
     * two distinct property table rows.
     */
    private static List<GlbPrimitiveBuilder.PrimitiveArrays> buildMainPrimitives(
            TriangleMesh mesh, VertexWelder.WeldResult weld,
            Map<Integer, Integer> texIdToPage, int pageCount, boolean hasTexture,
            CellFrame frame, ObjectStyleRegistry styleRegistry, boolean enableShading) {
        List<RoutedTriangle> routed = TriangleRouter.route(mesh, weld, styleRegistry);

        List<List<RoutedTriangle>> texturedTrisByPage = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            texturedTrisByPage.add(new ArrayList<>());
        }
        // LinkedHashMap so emitted plain primitives appear in the order the
        // styles were first encountered — stable across runs for a given
        // input.
        Map<DefaultObjectStyle, List<RoutedTriangle>> untexturedPlainTrisByStyle = new LinkedHashMap<>();
        List<RoutedTriangle> untexturedColoredTris = new ArrayList<>();
        for (RoutedTriangle rt : routed) {
            if (hasTexture && rt.textured()) {
                Integer page = texIdToPage.get(rt.textureId());
                // A texId missing from the map means the atlas builder dropped
                // this texture (e.g. corrupt source). Route the triangle to an
                // untextured bucket so the feature still renders.
                if (page != null) {
                    texturedTrisByPage.get(page).add(rt);
                    continue;
                }
            }
            if (rt.colored()) {
                untexturedColoredTris.add(rt);
            } else {
                untexturedPlainTrisByStyle.computeIfAbsent(rt.style(), k -> new ArrayList<>()).add(rt);
            }
        }

        // One primitive per non-empty atlas page + one plain primitive per
        // distinct DefaultObjectStyle in use + an optional colored primitive.
        // An empty page can arise when every triangle routed to it was dropped
        // by the weld/degenerate filter; its material + texture would still be
        // referenced in the glTF JSON, but no primitive would draw it, so we
        // simply skip it below and the JSON builder sees a compact set of
        // primitives.
        List<GlbPrimitiveBuilder.PrimitiveArrays> primitives = new ArrayList<>();
        for (int p = 0; p < pageCount; p++) {
            List<RoutedTriangle> tris = texturedTrisByPage.get(p);
            if (!tris.isEmpty()) {
                primitives.add(GlbPrimitiveBuilder.build(mesh, weld, tris, p, null, frame,
                        enableShading, true));
            }
        }
        for (Map.Entry<DefaultObjectStyle, List<RoutedTriangle>> e : untexturedPlainTrisByStyle.entrySet()) {
            if (!e.getValue().isEmpty()) {
                primitives.add(GlbPrimitiveBuilder.build(mesh, weld, e.getValue(),
                        GlbPrimitiveBuilder.UNTEXTURED_PLAIN_PAGE, e.getKey(), frame,
                        enableShading, true));
            }
        }
        if (!untexturedColoredTris.isEmpty()) {
            primitives.add(GlbPrimitiveBuilder.build(mesh, weld, untexturedColoredTris,
                    GlbPrimitiveBuilder.UNTEXTURED_COLORED_PAGE, null, frame,
                    enableShading, true));
        }
        return primitives;
    }

    private static List<GltfJsonBuilder.Primitive> toJsonPrimitives(
            List<GlbPrimitiveBuilder.PrimitiveArrays> primitives,
            List<GlbPrimitiveBuilder.PrimitiveBufferIds> bvs) {
        List<GltfJsonBuilder.Primitive> out = new ArrayList<>(primitives.size());
        for (int i = 0; i < primitives.size(); i++) {
            out.add(GlbPrimitiveBuilder.toJsonPrimitive(primitives.get(i), bvs.get(i), null));
        }
        return out;
    }
}
