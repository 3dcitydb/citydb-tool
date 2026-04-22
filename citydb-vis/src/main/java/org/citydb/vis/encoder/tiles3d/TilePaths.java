/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.scene.SceneNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes and formats tree-shaped file paths for 3D Tiles output.
 * <p>
 * Paths mirror the scene tree structure so neither the {@code tiles/} nor
 * the {@code subtrees/} directory accumulates a pathological number of
 * files per directory for massive datasets.
 * <p>
 * The path index is built from a single effective root (typically the
 * aggregation root produced by {@link CellAggregator}). The root's path
 * is {@code [0]} and every descendant extends its parent's path with its
 * child index: a grandchild of the root's second child is {@code [0, 1, i]}.
 * <p>
 * Example: aggregation root at {@code [0]} &rarr; its third child at
 * {@code [0, 2]} &rarr; that child's first child at {@code [0, 2, 0]}
 * maps to tile file {@code "0/2/0.glb"} and subtree file {@code "0/2/0.json"}.
 */
public final class TilePaths {

    private TilePaths() {
    }

    /**
     * Assign a path to every node reachable from the given root. The root
     * itself gets path {@code [0]}; children extend the parent's path with
     * their child index.
     */
    public static Map<Integer, int[]> buildPathIndex(SceneNode root) {
        Map<Integer, int[]> paths = new HashMap<>();
        walk(root, new int[]{0}, paths);
        return paths;
    }

    private static void walk(SceneNode node, int[] path, Map<Integer, int[]> paths) {
        paths.put(node.getIndex(), path);
        List<SceneNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            int[] childPath = new int[path.length + 1];
            System.arraycopy(path, 0, childPath, 0, path.length);
            childPath[path.length] = i;
            walk(children.get(i), childPath, paths);
        }
    }

    public static String tileFile(int[] path) {
        return format(path, 0, path.length, ".glb");
    }

    public static String subtreeFile(int[] path) {
        return format(path, 0, path.length, ".json");
    }

    /**
     * Relative sub-path of a node's parent directory (or {@code ""} for
     * top-level nodes whose files sit directly under the output root).
     */
    public static String parentDir(int[] path) {
        return format(path, 0, path.length - 1, "");
    }

    /**
     * Compute the relative URI from a subtree file at {@code from} to a
     * descendant subtree file at {@code to}. {@code to} must be a strict
     * descendant of {@code from} in the scene tree.
     */
    public static String relativeSubtreeUri(int[] from, int[] to) {
        if (from.length >= to.length
                || !Arrays.equals(from, 0, from.length, to, 0, from.length)) {
            throw new IllegalArgumentException(
                    "to must be a descendant of from in the scene tree");
        }
        return format(to, from.length - 1, to.length, ".json");
    }

    private static String format(int[] path, int start, int end, String extension) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) sb.append('/');
            sb.append(path[i]);
        }
        sb.append(extension);
        return sb.toString();
    }
}
