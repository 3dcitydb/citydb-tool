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
