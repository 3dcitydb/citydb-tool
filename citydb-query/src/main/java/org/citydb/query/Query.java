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

package org.citydb.query;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.SrsReference;
import org.citydb.database.schema.FeatureType;
import org.citydb.model.common.Name;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.feature.FeatureTypeProvider;
import org.citydb.query.feature.FeatureTypesReader;
import org.citydb.query.feature.FeatureTypesWriter;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.encoding.FilterConfigReader;
import org.citydb.query.filter.encoding.FilterConfigWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SerializableConfig(name = "query")
public class Query {
    @JSONField(serializeUsing = FeatureTypesWriter.class, deserializeUsing = FeatureTypesReader.class)
    private List<PrefixedName> featureTypes;
    @JSONField(serializeUsing = FilterConfigWriter.class, deserializeUsing = FilterConfigReader.class)
    private Filter filter;
    private SrsReference filterSrs;

    public boolean hasFeatureTypes() {
        return featureTypes != null && !featureTypes.isEmpty();
    }

    public List<PrefixedName> getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new ArrayList<>();
        }

        return featureTypes;
    }

    public Query setFeatureTypes(List<PrefixedName> featureTypes) {
        this.featureTypes = featureTypes;
        return this;
    }

    public Query addFeatureType(PrefixedName featureType) {
        if (featureType != null) {
            getFeatureTypes().add(featureType);
        }

        return this;
    }

    public Query addFeatureType(String name, String namespace) {
        return addFeatureType(PrefixedName.of(name, namespace));
    }

    public Query addFeatureType(String name) {
        return addFeatureType(PrefixedName.of(name));
    }

    public Query addFeatureType(Name name) {
        return addFeatureType(name.getLocalName(), name.getNamespace());
    }

    public Query addFeatureType(FeatureTypeProvider provider) {
        return addFeatureType(provider.getName());
    }

    public Query addFeatureType(FeatureType featureType) {
        return addFeatureType(featureType.getName());
    }

    public Optional<Filter> getFilter() {
        return Optional.ofNullable(filter);
    }

    public Query setFilter(Filter filter) {
        this.filter = filter;
        return this;
    }

    public Optional<SrsReference> getFilterSrs() {
        return Optional.ofNullable(filterSrs);
    }

    public Query setFilterSrs(SrsReference filterSrs) {
        this.filterSrs = filterSrs;
        return this;
    }
}
