/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline;

import org.citydb.vis.encoder.AttributeEncoder;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.scene.SceneNode;
import org.citydb.vis.store.PartitionedEntryStore;
import org.citydb.vis.store.VisExportStores;
import org.citydb.vis.config.VisFormatOptions;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable container shared across export pipeline stages. Earlier stages
 * populate fields that later stages consume.
 * <p>
 * Inputs (final, supplied by the writer):
 * <ul>
 *   <li>{@link #stores()} — disk-backed feature/mesh/attribute stores</li>
 *   <li>{@link #formatOptions()} — format-specific tuning parameters</li>
 *   <li>{@link #attributeEncoder()} — incremental attribute type tracker</li>
 *   <li>{@link #totalFeatures()} — total feature count (from spatial store)</li>
 * </ul>
 * Outputs (populated once during pipeline execution via setters):
 * <ul>
 *   <li>{@link #extent()} — global bounding box [minX,minY,minZ,maxX,maxY,maxZ]</li>
 *   <li>{@link #attrFields()} — finalized attribute field definitions</li>
 *   <li>{@link #partitioned()} — grid-partitioned spatial entries (closed by
 *       {@link org.citydb.vis.pipeline.stages.TreeBuildingStage})</li>
 *   <li>{@link #allNodes()} — flat list of scene nodes (index 0 = global root)</li>
 *   <li>{@link #meshNodeIndices()} — indices of nodes carrying geometry</li>
 *   <li>{@link #cellRootGridCoords()} — grid coordinates {@code [gy, gx]} of
 *       each cell-root scene node, consumed by
 *       {@link org.citydb.vis.pipeline.stages.AggregationStage} to place
 *       cells in the 2×2 aggregation tree</li>
 *   <li>{@link #hasTextures()} — whether any feature registered a texture</li>
 *   <li>{@link #hasColors()} — whether any mesh carries baked vertex colors
 *       (from X3DMaterial diffuseColor + transparency)</li>
 * </ul>
 */
public final class PipelineContext {
    private final VisExportStores stores;
    private final VisFormatOptions formatOptions;
    private final AttributeEncoder attributeEncoder;
    private final long totalFeatures;

    private double[] extent;
    private List<AttrField> attrFields;
    private PartitionedEntryStore partitioned;
    private List<SceneNode> allNodes;
    private Set<Integer> meshNodeIndices;
    private Map<Integer, int[]> cellRootGridCoords;
    private boolean hasTextures;
    private boolean hasColors;

    public PipelineContext(VisExportStores stores,
                           VisFormatOptions formatOptions,
                           AttributeEncoder attributeEncoder,
                           long totalFeatures) {
        this.stores = stores;
        this.formatOptions = formatOptions;
        this.attributeEncoder = attributeEncoder;
        this.totalFeatures = totalFeatures;
    }

    // ---- Inputs (read-only) -------------------------------------------------

    public VisExportStores stores() {
        return stores;
    }

    public VisFormatOptions formatOptions() {
        return formatOptions;
    }

    public AttributeEncoder attributeEncoder() {
        return attributeEncoder;
    }

    public long totalFeatures() {
        return totalFeatures;
    }

    // ---- Outputs (write-once) -----------------------------------------------

    public double[] extent() {
        return extent;
    }

    public PipelineContext setExtent(double[] extent) {
        this.extent = extent;
        return this;
    }

    public List<AttrField> attrFields() {
        return attrFields;
    }

    public PipelineContext setAttrFields(List<AttrField> attrFields) {
        this.attrFields = attrFields;
        return this;
    }

    public PartitionedEntryStore partitioned() {
        return partitioned;
    }

    public PipelineContext setPartitioned(PartitionedEntryStore partitioned) {
        this.partitioned = partitioned;
        return this;
    }

    public List<SceneNode> allNodes() {
        return allNodes;
    }

    public PipelineContext setAllNodes(List<SceneNode> allNodes) {
        this.allNodes = allNodes;
        return this;
    }

    public Set<Integer> meshNodeIndices() {
        return meshNodeIndices;
    }

    public PipelineContext setMeshNodeIndices(Set<Integer> meshNodeIndices) {
        this.meshNodeIndices = meshNodeIndices;
        return this;
    }

    public Map<Integer, int[]> cellRootGridCoords() {
        return cellRootGridCoords;
    }

    public PipelineContext setCellRootGridCoords(Map<Integer, int[]> cellRootGridCoords) {
        this.cellRootGridCoords = cellRootGridCoords;
        return this;
    }

    public boolean hasTextures() {
        return hasTextures;
    }

    public PipelineContext setHasTextures(boolean hasTextures) {
        this.hasTextures = hasTextures;
        return this;
    }

    public boolean hasColors() {
        return hasColors;
    }

    public PipelineContext setHasColors(boolean hasColors) {
        this.hasColors = hasColors;
        return this;
    }
}
