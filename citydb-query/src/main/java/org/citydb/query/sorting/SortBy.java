/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.sorting;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.query.filter.encoding.FilterParseException;
import org.citydb.query.filter.encoding.FilterTextParser;
import org.citydb.query.filter.literal.PropertyRef;

import java.util.Optional;

public class SortBy {
    @JSONField(serializeUsing = PropertyConfigWriter.class, deserializeUsing = PropertyConfigReader.class)
    private PropertyRef property;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private SortOrder sortOrder;

    public Optional<PropertyRef> getProperty() {
        return Optional.ofNullable(property);
    }

    @JSONField(name = "property")
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
