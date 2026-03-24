/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.scene;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an I3S 1.7+ node page containing a batch of node definitions.
 * Node pages improve random access performance for large scenes.
 */
public class NodePage {
    public static final int DEFAULT_PAGE_SIZE = 64;

    private final int pageIndex;
    private final List<NodePageEntry> nodes;

    public NodePage(int pageIndex) {
        this.pageIndex = pageIndex;
        this.nodes = new ArrayList<>();
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public List<NodePageEntry> getNodes() {
        return nodes;
    }

    public NodePage addNode(NodePageEntry entry) {
        nodes.add(entry);
        return this;
    }

    public boolean isFull() {
        return nodes.size() >= DEFAULT_PAGE_SIZE;
    }

    public static class NodePageEntry {
        private final int index;
        private final double[] mbs;
        private final int parentIndex;
        private final List<Integer> children;
        private final double lodThreshold;
        private final boolean hasGeometry;

        public NodePageEntry(int index, double[] mbs, int parentIndex,
                             List<Integer> children, double lodThreshold, boolean hasGeometry) {
            this.index = index;
            this.mbs = mbs;
            this.parentIndex = parentIndex;
            this.children = children;
            this.lodThreshold = lodThreshold;
            this.hasGeometry = hasGeometry;
        }

        public int getIndex() {
            return index;
        }

        public double[] getMbs() {
            return mbs;
        }

        public int getParentIndex() {
            return parentIndex;
        }

        public List<Integer> getChildren() {
            return children;
        }

        public double getLodThreshold() {
            return lodThreshold;
        }

        public boolean hasGeometry() {
            return hasGeometry;
        }
    }
}
