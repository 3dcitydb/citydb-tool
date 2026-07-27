/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.encoder.tiles3d;

import org.citydb.vis.scene.SceneNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tree-shaped path scheme shared by {@code tiles/} and {@code subtrees/}:
 * the effective root is {@code [0]}, children extend the parent path with
 * their child index, and relative subtree URIs are resolved against the
 * <em>containing file's directory</em> — the {@code from.length - 1} slice
 * that makes {@code subtrees/0.json} reference {@code 0/1.json} rather than
 * an absolute path. Getting any of this wrong breaks tile loading only at
 * runtime, silently.
 */
class TilePathsTest {

    @Test
    void pathIndexMirrorsTreeStructure() {
        SceneNode root = new SceneNode(10, 0);
        SceneNode childA = new SceneNode(11, 1);
        SceneNode childB = new SceneNode(12, 1);
        SceneNode grandchild = new SceneNode(13, 2);
        root.addChild(childA);
        root.addChild(childB);
        childB.addChild(grandchild);

        Map<Integer, int[]> paths = TilePaths.buildPathIndex(root);

        assertEquals(4, paths.size());
        assertArrayEquals(new int[]{0}, paths.get(10));
        assertArrayEquals(new int[]{0, 0}, paths.get(11));
        assertArrayEquals(new int[]{0, 1}, paths.get(12));
        assertArrayEquals(new int[]{0, 1, 0}, paths.get(13));
    }

    @Test
    void fileNamesJoinPathSegments() {
        assertEquals("0.glb", TilePaths.tileFile(new int[]{0}));
        assertEquals("0/2/0.glb", TilePaths.tileFile(new int[]{0, 2, 0}));
        assertEquals("0/2/0.json", TilePaths.subtreeFile(new int[]{0, 2, 0}));
        assertEquals("", TilePaths.parentDir(new int[]{0}));
        assertEquals("0/2", TilePaths.parentDir(new int[]{0, 2, 0}));
    }

    @Test
    void relativeSubtreeUriIsResolvedAgainstContainingDirectory() {
        // subtrees/0.json sits in subtrees/ — the URI to the child file
        // subtrees/0/1.json is "0/1.json".
        assertEquals("0/1.json",
                TilePaths.relativeSubtreeUri(new int[]{0}, new int[]{0, 1}));
        // subtrees/0/1.json sits in subtrees/0/ — the URI to the grandchild
        // subtrees/0/1/2.json drops the shared "0/" prefix.
        assertEquals("1/2.json",
                TilePaths.relativeSubtreeUri(new int[]{0, 1}, new int[]{0, 1, 2}));
    }

    @Test
    void nonDescendantTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                TilePaths.relativeSubtreeUri(new int[]{0, 1}, new int[]{0, 2, 0}));
        assertThrows(IllegalArgumentException.class, () ->
                TilePaths.relativeSubtreeUri(new int[]{0, 1}, new int[]{0, 1}));
    }
}
