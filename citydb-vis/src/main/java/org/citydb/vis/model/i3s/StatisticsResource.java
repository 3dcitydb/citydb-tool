/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.vis.encoder.AttrStats;

import java.util.ArrayList;
import java.util.List;

/**
 * Root object of a per-attribute statistics resource at
 * {@code layers/0/statistics/f_K/0/index.json}. The numeric flavour
 * carries {@code min} / {@code max} / {@code avg} / {@code stddev} /
 * {@code sum} / {@code variance}; the string flavour carries
 * {@code mostFrequentValues}. Built from an {@link AttrStats.Result}
 * snapshot.
 */
@JSONType(alphabetic = false)
public record StatisticsResource(Stats stats) {

    public static StatisticsResource of(AttrStats.Result result) {
        return new StatisticsResource(Stats.from(result));
    }

    @JSONType(alphabetic = false)
    public record Stats(long totalValuesCount,
                        Double min, Double max, Double avg, Double stddev,
                        Double sum, Double variance,
                        List<MostFrequentValue> mostFrequentValues) {

        static Stats from(AttrStats.Result r) {
            if (r.isNumeric()) {
                return new Stats(r.totalValuesCount(),
                        r.numericCount() == 0 ? null : r.min(),
                        r.numericCount() == 0 ? null : r.max(),
                        r.numericCount() == 0 ? null : r.avg(),
                        r.numericCount() == 0 ? null : r.stddev(),
                        r.numericCount() == 0 ? null : r.sum(),
                        r.numericCount() == 0 ? null : r.variance(),
                        null);
            }
            List<MostFrequentValue> entries = new ArrayList<>(r.frequency().size());
            for (AttrStats.FrequencyEntry e : r.frequency()) {
                entries.add(new MostFrequentValue(e.value(), e.count()));
            }
            return new Stats(r.totalValuesCount(), null, null, null, null, null, null, entries);
        }
    }

    @JSONType(alphabetic = false)
    public record MostFrequentValue(String value, long count) {
    }
}
