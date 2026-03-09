/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
    public static final int DEFAULT_COMMIT_AFTER = 1000;

    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private DeleteMode mode = DeleteMode.TERMINATE;
    @JSONField(serialize = false, deserialize = false)
    private int numberOfThreads;
    private int commitAfter;
    private boolean terminateWithSubFeatures = true;
    private OffsetDateTime terminationDate;
    private String updatingPerson;
    private String reasonForUpdate;
    private String lineage;

    public DeleteMode getMode() {
        return mode != null ? mode : DeleteMode.TERMINATE;
    }

    public DeleteOptions setMode(DeleteMode mode) {
        this.mode = mode;
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public DeleteOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public int getCommitAfter() {
        return commitAfter;
    }

    public DeleteOptions setCommitAfter(int commitAfter) {
        this.commitAfter = commitAfter;
        return this;
    }

    public boolean isTerminateWithSubFeatures() {
        return terminateWithSubFeatures;
    }

    public DeleteOptions setTerminateWithSubFeatures(boolean terminateWithSubFeatures) {
        this.terminateWithSubFeatures = terminateWithSubFeatures;
        return this;
    }

    public Optional<OffsetDateTime> getTerminationDate() {
        return Optional.ofNullable(terminationDate);
    }

    public DeleteOptions setTerminationDate(OffsetDateTime terminationDate) {
        this.terminationDate = terminationDate;
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
}
