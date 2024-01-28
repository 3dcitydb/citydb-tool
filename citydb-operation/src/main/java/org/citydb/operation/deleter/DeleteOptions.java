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

package org.citydb.operation.deleter;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.operation.deleter.options.DeleteMode;

import java.time.OffsetDateTime;
import java.util.Optional;

@SerializableConfig(name = "deleteOptions")
public class DeleteOptions {
    private int numberOfThreads;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private DeleteMode mode = DeleteMode.DELETE;
    private String updatingPerson;
    private String reasonForUpdate;
    private String lineage;
    private OffsetDateTime terminationDate;

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public DeleteOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public DeleteMode getMode() {
        return mode != null ? mode : DeleteMode.DELETE;
    }

    public DeleteOptions setMode(DeleteMode mode) {
        this.mode = mode;
        return this;
    }

    public Optional<String> getUpdatingPerson() {
        return Optional.ofNullable(updatingPerson);
    }

    public DeleteOptions setUpdatingPerson(String updatingPerson) {
        this.updatingPerson = updatingPerson;
        return this;
    }

    public Optional<String> getReasonForUpdate() {
        return Optional.ofNullable(reasonForUpdate);
    }

    public DeleteOptions setReasonForUpdate(String reasonForUpdate) {
        this.reasonForUpdate = reasonForUpdate;
        return this;
    }

    public Optional<String> getLineage() {
        return Optional.ofNullable(lineage);
    }

    public DeleteOptions setLineage(String lineage) {
        this.lineage = lineage;
        return this;
    }

    public Optional<OffsetDateTime> getTerminationDate() {
        return Optional.ofNullable(terminationDate);
    }

    public DeleteOptions setTerminationDate(OffsetDateTime terminationDate) {
        this.terminationDate = terminationDate;
        return this;
    }
}
