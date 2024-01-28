/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.operation.importer.util;

import org.citydb.operation.util.FeatureStatistics;

import java.util.Objects;
import java.util.function.Consumer;

public class StatisticsConsumer implements Consumer<FeatureStatistics> {
    private final Consumer<FeatureStatistics> consumer;
    private final Mode mode;

    public enum Mode {
        COUNT_COMMITTED,
        COUNT_ALL
    }

    private StatisticsConsumer(Consumer<FeatureStatistics> consumer, Mode mode) {
        this.consumer = Objects.requireNonNull(consumer, "The feature statistics consumer must not be null.");
        this.mode = mode != null ? mode : Mode.COUNT_COMMITTED;
    }

    public static StatisticsConsumer of(Consumer<FeatureStatistics> consumer, Mode mode) {
        return new StatisticsConsumer(consumer, mode);
    }

    public static StatisticsConsumer of(Consumer<FeatureStatistics> consumer) {
        return new StatisticsConsumer(consumer, Mode.COUNT_COMMITTED);
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    public void accept(FeatureStatistics statistics) {
        consumer.accept(statistics);
    }
}
