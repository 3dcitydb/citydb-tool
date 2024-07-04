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

package org.citydb.query.sorting;

import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.literal.PropertyRef;

import java.util.ArrayList;
import java.util.List;

public class Sorting {
    private List<SortBy> sortBy;

    public boolean hasSortBy() {
        return sortBy != null && !sortBy.isEmpty();
    }

    public List<SortBy> getSortBy() {
        if (sortBy == null) {
            sortBy = new ArrayList<>();
        }

        return sortBy;
    }

    public Sorting setSortBy(List<SortBy> sortBy) {
        this.sortBy = sortBy;
        return this;
    }

    public Sorting addSortBy(SortBy sortBy) {
        if (sortBy != null) {
            getSortBy().add(sortBy);
        }

        return this;
    }

    public Sorting addSortBy(PropertyRef property) {
        return addSortBy(property, null);
    }

    public Sorting addSortBy(PropertyRef property, SortOrder sortOrder) {
        return addSortBy(new SortBy()
                .setProperty(property)
                .setSortOrder(sortOrder));
    }

    public Sorting addSortBy(String propertyRef) throws FilterParseException {
        return addSortBy(propertyRef, null);
    }

    public Sorting addSortBy(String propertyRef, SortOrder sortOrder) throws FilterParseException {
        return addSortBy(new SortBy()
                .setProperty(propertyRef)
                .setSortOrder(sortOrder));
    }
}
