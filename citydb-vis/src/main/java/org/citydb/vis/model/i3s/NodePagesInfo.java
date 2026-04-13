/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(alphabetic = false)
public record NodePagesInfo(int nodesPerPage, String lodSelectionMetricType) {
    public static NodePagesInfo defaults() {
        return new NodePagesInfo(I3SConstants.NODES_PER_PAGE, "maxScreenThresholdSQ");
    }
}
