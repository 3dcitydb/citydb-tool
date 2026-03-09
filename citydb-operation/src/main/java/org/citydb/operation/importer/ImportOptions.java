/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.encoding.Matrix3x4Reader;
import org.citydb.model.encoding.Matrix3x4Writer;
import org.citydb.operation.importer.options.CreationDateMode;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;

@SerializableConfig(name = "importOptions")
public class ImportOptions {
    private boolean failFast;
    private String tempDirectory;
    private int numberOfThreads;
    private String updatingPerson;
    private String reasonForUpdate;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private CreationDateMode creationDateMode = CreationDateMode.ATTRIBUTE_OR_NOW;
    private OffsetDateTime creationDate;
    private String lineage;
    @JSONField(serializeUsing = Matrix3x4Writer.class, deserializeUsing = Matrix3x4Reader.class)
    private Matrix3x4 affineTransform;

    public boolean isFailFast() {
        return failFast;
    }

    public ImportOptions setFailFast(boolean failFast) {
        this.failFast = failFast;
        return this;
    }

    public Optional<Path> getTempDirectory() {
        return Optional.ofNullable(tempDirectory != null ? Path.of(tempDirectory) : null);
    }

    public ImportOptions setTempDirectory(Path tempDirectory) {
        return setTempDirectory(tempDirectory != null ? tempDirectory.toString() : null);
    }

    public ImportOptions setTempDirectory(String tempDirectory) {
        this.tempDirectory = tempDirectory;
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public ImportOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public Optional<String> getUpdatingPerson() {
        return Optional.ofNullable(updatingPerson);
    }

    public ImportOptions setUpdatingPerson(String updatingPerson) {
        this.updatingPerson = updatingPerson;
        return this;
    }

    public Optional<String> getReasonForUpdate() {
        return Optional.ofNullable(reasonForUpdate);
    }

    public ImportOptions setReasonForUpdate(String reasonForUpdate) {
        this.reasonForUpdate = reasonForUpdate;
        return this;
    }

    public CreationDateMode getCreationDateMode() {
        return creationDateMode != null ? creationDateMode : CreationDateMode.ATTRIBUTE_OR_NOW;
    }

    public ImportOptions setCreationDateMode(CreationDateMode creationDateMode) {
        this.creationDateMode = creationDateMode;
        return this;
    }

    public Optional<OffsetDateTime> getCreationDate() {
        return Optional.ofNullable(creationDate);
    }

    public ImportOptions setCreationDate(OffsetDateTime creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public Optional<String> getLineage() {
        return Optional.ofNullable(lineage);
    }

    public ImportOptions setLineage(String lineage) {
        this.lineage = lineage;
        return this;
    }

    public Optional<Matrix3x4> getAffineTransform() {
        return Optional.ofNullable(affineTransform);
    }

    public ImportOptions setAffineTransform(Matrix3x4 affineTransform) {
        this.affineTransform = affineTransform;
        return this;
    }
}
