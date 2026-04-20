/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
 * Each cell-root scene node starts at a caller-supplied path (typically its
 * grid coordinates {@code [gy, gx]}); descendants extend it with their
 * child index. This drives both the nested {@code tiles/} and
 * {@code subtrees/} directory structures, so neither folder accumulates a
 * pathological number of files per directory for massive datasets.
 * <p>
 * Example: cell root at grid (gy=2, gx=1) &rarr; child 3 maps to
 * path {@code [2, 1, 3]}, tile file {@code "2/1/3.glb"}, subtree file
 * {@code "2/1/3.json"}.
 */
public final class TilePaths {

    private TilePaths() {
    }

    public static Map<Integer, int[]> buildPathIndex(SceneNode globalRoot,
                                                     Map<Integer, int[]> cellRootStartPaths) {
        Map<Integer, int[]> paths = new HashMap<>();
        for (SceneNode cellRoot : globalRoot.getChildren()) {
            int[] startPath = cellRootStartPaths.get(cellRoot.getIndex());
            walk(cellRoot, startPath, paths);
        }
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
     * cell-root nodes, whose files sit directly under the output root).
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
