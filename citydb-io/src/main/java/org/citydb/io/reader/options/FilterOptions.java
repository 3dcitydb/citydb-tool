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

package org.citydb.io.reader.options;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.common.CountLimit;
import org.citydb.model.common.Name;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.encoding.EnvelopeReader;
import org.citydb.model.encoding.EnvelopeWriter;
import org.citydb.model.encoding.PrefixedNamesReader;
import org.citydb.model.encoding.PrefixedNamesWriter;
import org.citydb.model.feature.FeatureTypeProvider;
import org.citydb.model.geometry.Envelope;

import java.util.*;

public class FilterOptions {
    @JSONField(serializeUsing = PrefixedNamesWriter.class, deserializeUsing = PrefixedNamesReader.class)
    private List<PrefixedName> featureTypes;
    private Set<String> ids;
    @JSONField(serializeUsing = EnvelopeWriter.class, deserializeUsing = EnvelopeReader.class)
    private Envelope bbox;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private BboxMode bboxMode;
    private CountLimit countLimit;

    public boolean hasFeatureTypes() {
        return featureTypes != null && !featureTypes.isEmpty();
    }

    public List<PrefixedName> getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new ArrayList<>();
        }

        return featureTypes;
    }

    public FilterOptions setFeatureTypes(List<PrefixedName> featureTypes) {
        this.featureTypes = featureTypes;
        return this;
    }

    public FilterOptions addFeatureType(PrefixedName featureType) {
        if (featureType != null) {
            getFeatureTypes().add(featureType);
        }

        return this;
    }

    public FilterOptions addFeatureType(String name, String namespace) {
        return addFeatureType(PrefixedName.of(name, namespace));
    }

    public FilterOptions addFeatureType(String name) {
        return addFeatureType(PrefixedName.of(name));
    }

    public FilterOptions addFeatureType(Name name) {
        return addFeatureType(name.getLocalName(), name.getNamespace());
    }

    public FilterOptions addFeatureType(FeatureTypeProvider provider) {
        return addFeatureType(provider.getName());
    }

    public boolean hasIds() {
        return ids != null && !ids.isEmpty();
    }

    public Set<String> getIds() {
        if (ids == null) {
            ids = new HashSet<>();
        }

        return ids;
    }

    public FilterOptions setIds(Set<String> ids) {
        this.ids = ids;
        return this;
    }

    public FilterOptions addId(String id) {
        if (id != null) {
            getIds().add(id);
        }

        return this;
    }

    public Optional<Envelope> getBbox() {
        return Optional.ofNullable(bbox);
    }

    public FilterOptions setBbox(Envelope bbox) {
        this.bbox = bbox;
        return this;
    }

    public BboxMode getBboxMode() {
        return bboxMode != null ? bboxMode : BboxMode.INTERSECTS;
    }

    public FilterOptions setBboxMode(BboxMode bboxMode) {
        this.bboxMode = bboxMode;
        return this;
    }

    public Optional<CountLimit> getCountLimit() {
        return Optional.ofNullable(countLimit);
    }

    public FilterOptions setCountLimit(CountLimit countLimit) {
        this.countLimit = countLimit;
        return this;
    }
}
