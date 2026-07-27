/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.scene;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct characterization of the 2×2 aggregation build: fanout stays within
 * {@link CellAggregator#MAX_CHILDREN_PER_AGG_NODE}, recursion terminates on
 * collapsed axes and duplicate grid coordinates, no cell is ever lost, and
 * synthetic indices come exclusively from the caller's indexer (contiguous,
 * so the stage can register them under the list-index invariant).
 */
class CellAggregatorTest {

    @Test
    void singleCellIsWrappedForUniformOutputPaths() {
        List<SceneNode> cells = List.of(cell(1, 0, 0));
        AtomicInteger indexer = new AtomicInteger(10);

        SceneNode agg = CellAggregator.build(cells, coordsOf(cells), indexer);

        assertEquals(10, agg.getIndex());
        assertEquals(11, indexer.get());
        assertEquals(1, agg.getChildren().size());
        assertSame(cells.get(0), agg.getChildren().get(0));
        // Bbox propagates from the only child.
        assertEquals(0.0, agg.getBoundingVolume().getMinX());
        assertEquals(1.0, agg.getBoundingVolume().getMaxX());
    }

    @Test
    void upToFourCellsCollapseUnderOneNode() {
        List<SceneNode> cells = grid(2, 2);
        AtomicInteger indexer = new AtomicInteger(5);

        SceneNode agg = CellAggregator.build(cells, coordsOf(cells), indexer);

        // Exactly one synthetic node consumed from the indexer.
        assertEquals(6, indexer.get());
        assertEquals(4, agg.getChildren().size());
        assertTrue(agg.getChildren().containsAll(cells));
    }

    @Test
    void nineCellsRecurseWithBoundedFanoutAndNoCellLost() {
        List<SceneNode> cells = grid(3, 3);
        // grid() indexes cells 1..n, so any index >= n + 1 is synthetic.
        int firstSyntheticIndex = cells.size() + 1;
        AtomicInteger indexer = new AtomicInteger(firstSyntheticIndex);

        SceneNode agg = CellAggregator.build(cells, coordsOf(cells), indexer);

        Set<SceneNode> reachableCells = new HashSet<>();
        int syntheticCount = 0;
        Deque<SceneNode> queue = new ArrayDeque<>(List.of(agg));
        while (!queue.isEmpty()) {
            SceneNode node = queue.poll();
            if (node.getIndex() >= firstSyntheticIndex) {
                syntheticCount++;
                assertNotNull(node.getBoundingVolume());
                assertTrue(node.getChildren().size() <= CellAggregator.MAX_CHILDREN_PER_AGG_NODE,
                        "aggregation fanout must stay within the 2×2 limit");
                queue.addAll(node.getChildren());
            } else {
                reachableCells.add(node);
            }
        }
        assertEquals(new HashSet<>(cells), reachableCells);
        // Indexer consumption matches the synthetic node count — the stage
        // relies on this to append them contiguously to allNodes.
        assertEquals(firstSyntheticIndex + syntheticCount, indexer.get());
        assertTrue(syntheticCount > 1, "9 cells must recurse into sub-aggregations");

        // Root bbox is the union of the full 3×3 layout.
        assertEquals(0.0, agg.getBoundingVolume().getMinX());
        assertEquals(0.0, agg.getBoundingVolume().getMinY());
        assertEquals(3.0, agg.getBoundingVolume().getMaxX());
        assertEquals(3.0, agg.getBoundingVolume().getMaxY());
    }

    @Test
    void singleRowSplitsOnTheRemainingAxis() {
        // 6 cells in one row: gy is collapsed, recursion must still terminate
        // by splitting on gx alone.
        List<SceneNode> cells = grid(1, 6);
        // grid() indexes cells 1..n, so any index >= n + 1 is synthetic.
        int firstSyntheticIndex = cells.size() + 1;
        AtomicInteger indexer = new AtomicInteger(firstSyntheticIndex);

        SceneNode agg = CellAggregator.build(cells, coordsOf(cells), indexer);

        // The gx midpoint of 0..5 is 2, mid inclusive on the low side: the
        // root splits into a gx 0-2 and a gx 3-5 sub-aggregation instead of
        // collapsing all 6 cells under itself.
        assertEquals(2, agg.getChildren().size());

        Set<SceneNode> reachableCells = new HashSet<>();
        int syntheticCount = 0;
        Deque<SceneNode> queue = new ArrayDeque<>(List.of(agg));
        while (!queue.isEmpty()) {
            SceneNode node = queue.poll();
            if (node.getIndex() >= firstSyntheticIndex) {
                syntheticCount++;
                assertTrue(node.getChildren().size() <= CellAggregator.MAX_CHILDREN_PER_AGG_NODE,
                        "a collapsed gy must not disable the split on gx");
                queue.addAll(node.getChildren());
            } else {
                reachableCells.add(node);
            }
        }
        assertEquals(new HashSet<>(cells), reachableCells);
        assertEquals(3, syntheticCount);
        assertEquals(firstSyntheticIndex + syntheticCount, indexer.get());
    }

    @Test
    void identicalGridCoordsCannotSplitAndCollapseUnderOneNode() {
        // More cells than the fanout limit but all on one grid coordinate:
        // the canSplit guard must terminate recursion instead of looping.
        List<SceneNode> cells = new ArrayList<>();
        Map<Integer, int[]> coords = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            SceneNode node = cell(i, 0, 0);
            cells.add(node);
            coords.put(i, new int[]{0, 0});
        }
        AtomicInteger indexer = new AtomicInteger(10);

        SceneNode agg = CellAggregator.build(cells, coords, indexer);

        assertEquals(5, agg.getChildren().size());
        assertEquals(11, indexer.get());
    }

    @Test
    void cellWithoutGridCoordsIsRejected() {
        List<SceneNode> cells = List.of(cell(1, 0, 0));

        assertThrows(IllegalStateException.class,
                () -> CellAggregator.build(cells, Map.of(), new AtomicInteger(10)));
    }

    /** Cell root indexed {@code index} whose bbox is its unit grid square. */
    private static SceneNode cell(int index, int gy, int gx) {
        SceneNode node = new SceneNode(index, 1);
        node.setBoundingVolume(BoundingVolume.ofBoundingBox(
                gx, gy, 0.0, gx + 1.0, gy + 1.0, 10.0));
        return node;
    }

    private static List<SceneNode> grid(int rows, int cols) {
        List<SceneNode> cells = new ArrayList<>();
        int index = 1;
        for (int gy = 0; gy < rows; gy++) {
            for (int gx = 0; gx < cols; gx++) {
                cells.add(cell(index++, gy, gx));
            }
        }
        return cells;
    }

    /** Recover each cell's [gy, gx] from the unit-square bbox set by {@link #cell}. */
    private static Map<Integer, int[]> coordsOf(List<SceneNode> cells) {
        Map<Integer, int[]> coords = new HashMap<>();
        for (SceneNode node : cells) {
            coords.put(node.getIndex(), new int[]{
                    (int) node.getBoundingVolume().getMinY(),
                    (int) node.getBoundingVolume().getMinX()});
        }
        return coords;
    }
}
