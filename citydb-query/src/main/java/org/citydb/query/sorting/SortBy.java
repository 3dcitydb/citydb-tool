/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.sorting;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.encoding.FilterTextParser;
import org.citydb.query.filter.literal.PropertyRef;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.util.Optional;

public class SortBy {
    @JsonSerialize(using = PropertyConfigWriter.class)
    @JsonDeserialize(using = PropertyConfigReader.class)
    private PropertyRef property;
    private SortOrder sortOrder;

    public Optional<PropertyRef> getProperty() {
        return Optional.ofNullable(property);
    }

    @JsonProperty("property")
    public SortBy setProperty(PropertyRef property) {
        this.property = property;
        return this;
    }

    public SortBy setProperty(String propertyRef) throws FilterParseException {
        this.property = FilterTextParser.newInstance().parse(propertyRef, PropertyRef.class);
        return this;
    }

    public SortOrder getSortOrder() {
        return sortOrder != null ? sortOrder : SortOrder.ASC;
    }

    public SortBy setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }
}
