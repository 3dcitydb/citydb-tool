/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model;

import org.citydb.vis.geometry.TriangleMesh;

import java.util.List;

/**
 * All instances of one implicit-geometry prototype within a node: the shared
 * template mesh plus the per-instance placements. The 3D Tiles encoder emits
 * one glTF node per batch — the prototype mesh encoded once, placed via
 * {@code EXT_mesh_gpu_instancing} TRS attributes, with per-instance feature
 * identity via {@code EXT_instance_features}.
 *
 * @param prototypeId   registry id of the shared template
 * @param prototypeMesh template mesh in local Cartesian meters (X=east,
 *                      Y=north, Z=up at unit scale; never mutated here)
 * @param weldTolerance weld distance for the template mesh in template-local
 *                      units, pre-divided by the largest per-instance scale
 *                      so the effective world-space tolerance never exceeds
 *                      the default 2 cm the baked path applies post-transform
 * @param instances     placements, in node-entry order
 */
public record InstanceBatch(int prototypeId, TriangleMesh prototypeMesh,
                            float weldTolerance,
                            List<InstancedFeature> instances) {
}
