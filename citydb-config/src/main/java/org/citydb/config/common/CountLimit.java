/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
