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

package org.citydb.cli.deleter;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.cli.common.IndexMode;
import org.citydb.config.SerializableConfig;
import org.citydb.operation.exporter.options.ValidityOptions;
import org.citydb.query.Query;

import java.util.Optional;

@SerializableConfig(name = "deleteOptions")
public class DeleteOptions extends org.citydb.operation.deleter.DeleteOptions {
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private IndexMode indexMode = IndexMode.KEEP;
    private Query query;
    private ValidityOptions validityOptions;

    public IndexMode getIndexMode() {
        return indexMode != null ? indexMode : IndexMode.KEEP;
    }

    public DeleteOptions setIndexMode(IndexMode indexMode) {
        this.indexMode = indexMode;
        return this;
    }

    public Optional<Query> getQuery() {
        return Optional.ofNullable(query);
    }

    public DeleteOptions setQuery(Query query) {
        this.query = query;
        return this;
    }

    public Optional<ValidityOptions> getValidityOptions() {
        return Optional.ofNullable(validityOptions);
    }

    public DeleteOptions setValidityOptions(ValidityOptions validityOptions) {
        this.validityOptions = validityOptions;
        return this;
    }
}
