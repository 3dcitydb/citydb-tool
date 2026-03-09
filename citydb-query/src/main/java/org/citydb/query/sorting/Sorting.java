/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
