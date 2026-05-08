/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

/**
 * Single entry of the layer's {@code statisticsInfo[]} array. Pairs an
 * {@link AttributeStorageInfo} entry (by matching {@code key}) with the
 * relative URL of the per-attribute statistics resource. ArcGIS Pro
 * reads {@code statisticsInfo} to drive its attribute-filter UI;
 * without these entries the SLPK validator emits an advisory
 * {@code MISSING_ATTRIBUTE_STATS_DECL} warning per declared attribute.
 * <p>
 * Path conventions confirmed empirically against the i3s-validator:
 * <ul>
 *   <li>{@code key} = {@code "f_K"} (matches the corresponding
 *       {@link AttributeStorageInfo} entry exactly; mismatch fires
 *       {@code STATS_DECL_UNKNOWN_ATTRIBUTE}).</li>
 *   <li>{@code href} = {@code "statistics/f_K/0"} — no {@code "./"}
 *       prefix, no {@code "_0"} suffix on the directory. Both
 *       {@code "./statistics/f_K/0"} and {@code "./statistics/f_K_0/0"}
 *       trigger {@code PATH_COMPATIBILITY_WARNING}.</li>
 * </ul>
 */
@JSONType(alphabetic = false)
public record StatisticsInfo(String key, String name, String href) {

    public static StatisticsInfo of(int index, String fieldName) {
        return new StatisticsInfo("f_" + index, fieldName,
                "statistics/f_" + index + "/0");
    }
}
