/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.config.common;

import java.util.Optional;

public class CountLimit {
    private Long limit;
    private Long startIndex;

    public Optional<Long> getLimit() {
        return Optional.ofNullable(limit);
    }

    public CountLimit setLimit(Long limit) {
        this.limit = limit != null && limit >= 0 ? limit : null;
        return this;
    }

    public CountLimit setLimit(int count) {
        return setLimit((long) count);
    }

    public Optional<Long> getStartIndex() {
        return Optional.ofNullable(startIndex);
    }

    public CountLimit setStartIndex(Long startIndex) {
        this.startIndex = startIndex != null && startIndex >= 0 ? startIndex : null;
        return this;
    }

    public CountLimit setStartIndex(int startIndex) {
        return setStartIndex((long) startIndex);
    }
}
