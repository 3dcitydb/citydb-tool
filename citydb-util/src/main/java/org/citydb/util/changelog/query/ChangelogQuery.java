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

package org.citydb.util.changelog.query;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.SrsReference;
import org.citydb.model.change.TransactionType;
import org.citydb.model.common.Name;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.encoding.EnvelopeReader;
import org.citydb.model.encoding.EnvelopeWriter;
import org.citydb.model.encoding.PrefixedNamesReader;
import org.citydb.model.encoding.PrefixedNamesWriter;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.feature.FeatureTypeProvider;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.changelog.options.BboxMode;
import org.citydb.util.changelog.options.SortOrder;
import org.citydb.util.changelog.options.TransactionDate;

import java.time.OffsetDateTime;
import java.util.*;

@SerializableConfig(name = "changelogQuery")
public class ChangelogQuery {
    @JSONField(serializeUsing = PrefixedNamesWriter.class, deserializeUsing = PrefixedNamesReader.class)
    private List<PrefixedName> featureTypes;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private Set<TransactionType> transactionTypes;
    private TransactionDate transactionDate;
    private Set<String> ids;
    @JSONField(serializeUsing = EnvelopeWriter.class, deserializeUsing = EnvelopeReader.class)
    private Envelope bbox;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private BboxMode bboxMode;
    private Set<String> databaseUsers;
    private Set<String> reasonsForUpdate;
    @JSONField(name = "sql")
    private String sqlFilter;
    private SrsReference targetSrs;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private SortOrder sortOrder;

    public static ChangelogQuery newInstance() {
        return new ChangelogQuery();
    }

    public static ChangelogQuery after(OffsetDateTime after) {
        return new ChangelogQuery().setTransactionDate(TransactionDate.after(after));
    }

    public static ChangelogQuery until(OffsetDateTime until) {
        return new ChangelogQuery().setTransactionDate(TransactionDate.until(until));
    }

    public static ChangelogQuery range(OffsetDateTime after, OffsetDateTime until) {
        return new ChangelogQuery().setTransactionDate(TransactionDate.range(after, until));
    }

    public boolean hasFeatureTypes() {
        return featureTypes != null && !featureTypes.isEmpty();
    }

    public List<PrefixedName> getFeatureTypes() {
        if (featureTypes == null) {
            featureTypes = new ArrayList<>();
        }

        return featureTypes;
    }

    public ChangelogQuery setFeatureTypes(List<PrefixedName> featureTypes) {
        this.featureTypes = featureTypes;
        return this;
    }

    public ChangelogQuery addFeatureType(PrefixedName featureType) {
        if (featureType != null) {
            getFeatureTypes().add(featureType);
        }

        return this;
    }

    public ChangelogQuery addFeatureType(String name, String namespace) {
        return addFeatureType(PrefixedName.of(name, namespace));
    }

    public ChangelogQuery addFeatureType(String name) {
        return addFeatureType(PrefixedName.of(name));
    }

    public ChangelogQuery addFeatureType(Name name) {
        return addFeatureType(name.getLocalName(), name.getNamespace());
    }

    public ChangelogQuery addFeatureType(FeatureTypeProvider provider) {
        return addFeatureType(provider.getName());
    }

    public ChangelogQuery addFeatureType(FeatureType featureType) {
        return addFeatureType(featureType.getName());
    }

    public boolean hasTransactionTypes() {
        return transactionTypes != null && !transactionTypes.isEmpty();
    }

    public Set<TransactionType> getTransactionTypes() {
        if (transactionTypes == null) {
            transactionTypes = new HashSet<>();
        }

        return transactionTypes;
    }

    public ChangelogQuery setTransactionTypes(Set<TransactionType> transactionTypes) {
        this.transactionTypes = transactionTypes;
        return this;
    }

    public ChangelogQuery addTransactionType(TransactionType transactionType) {
        if (transactionType != null) {
            getTransactionTypes().add(transactionType);
        }

        return this;
    }

    public Optional<TransactionDate> getTransactionDate() {
        return Optional.ofNullable(transactionDate);
    }

    public ChangelogQuery setTransactionDate(TransactionDate transactionDate) {
        this.transactionDate = transactionDate;
        return this;
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

    public ChangelogQuery setIds(Set<String> ids) {
        this.ids = ids;
        return this;
    }

    public ChangelogQuery addId(String id) {
        if (id != null) {
            getIds().add(id);
        }

        return this;
    }

    public Optional<Envelope> getBbox() {
        return Optional.ofNullable(bbox);
    }

    public ChangelogQuery setBbox(Envelope bbox) {
        this.bbox = bbox;
        return this;
    }

    public BboxMode getBboxMode() {
        return bboxMode != null ? bboxMode : BboxMode.INTERSECTS;
    }

    public ChangelogQuery setBboxMode(BboxMode bboxMode) {
        this.bboxMode = bboxMode;
        return this;
    }

    public boolean hasDatabaseUsers() {
        return databaseUsers != null && !databaseUsers.isEmpty();
    }

    public Set<String> getDatabaseUsers() {
        if (databaseUsers == null) {
            databaseUsers = new HashSet<>();
        }

        return databaseUsers;
    }

    public ChangelogQuery setDatabaseUsers(Set<String> databaseUsers) {
        this.databaseUsers = databaseUsers;
        return this;
    }

    public ChangelogQuery addDatabaseUser(String databaseUser) {
        if (databaseUser != null) {
            getDatabaseUsers().add(databaseUser);
        }

        return this;
    }

    public boolean hasReasonsForUpdate() {
        return reasonsForUpdate != null && !reasonsForUpdate.isEmpty();
    }

    public Set<String> getReasonsForUpdate() {
        if (reasonsForUpdate == null) {
            reasonsForUpdate = new HashSet<>();
        }

        return reasonsForUpdate;
    }

    public ChangelogQuery setReasonsForUpdate(Set<String> reasonsForUpdate) {
        this.reasonsForUpdate = reasonsForUpdate;
        return this;
    }

    public ChangelogQuery addReasonForUpdate(String reasonForUpdate) {
        if (reasonForUpdate != null) {
            getReasonsForUpdate().add(reasonForUpdate);
        }

        return this;
    }

    public Optional<String> getSqlFilter() {
        return Optional.ofNullable(sqlFilter);
    }

    public ChangelogQuery setSqlFilter(String sqlFilter) {
        this.sqlFilter = sqlFilter;
        return this;
    }

    public Optional<SrsReference> getTargetSrs() {
        return Optional.ofNullable(targetSrs);
    }

    public ChangelogQuery setTargetSrs(SrsReference targetSrs) {
        this.targetSrs = targetSrs;
        return this;
    }

    public Optional<SortOrder> getSortOrder() {
        return Optional.ofNullable(sortOrder);
    }

    public ChangelogQuery setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        return this;
    }
}
