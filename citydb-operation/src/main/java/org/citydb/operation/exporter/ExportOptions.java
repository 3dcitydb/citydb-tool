/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter;

import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.SrsReference;
import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.encoding.Matrix3x4Reader;
import org.citydb.model.encoding.Matrix3x4Writer;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.operation.exporter.options.LodOptions;
import org.citydb.operation.exporter.options.ValidityOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@SerializableConfig(name = "exportOptions")
public class ExportOptions {
    @JSONField(serialize = false, deserialize = false)
    private final LazyCheckedInitializer<OutputFile, IOException> tempOutputFile = LazyCheckedInitializer.of(
            () -> new RegularOutputFile(Files.createTempDirectory("citydb-").resolve("output.tmp")));

    @JSONField(serialize = false, deserialize = false)
    private OutputFile outputFile;
    private int numberOfThreads;
    private boolean useAbsoluteResourcePaths;
    private SrsReference targetSrs;
    @JSONField(serializeUsing = Matrix3x4Writer.class, deserializeUsing = Matrix3x4Reader.class)
    private Matrix3x4 affineTransform;
    private ValidityOptions validityOptions;
    private LodOptions lodOptions;
    private AppearanceOptions appearanceOptions;

    public OutputFile getOutputFile() {
        if (outputFile == null) {
            try {
                outputFile = tempOutputFile.get();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temporary output directory.", e);
            }
        }

        return outputFile;
    }

    public ExportOptions setOutputFile(OutputFile outputFile) {
        this.outputFile = outputFile;
        return this;
    }

    public ExportOptions setOutputDirectory(Path outputDirectory) {
        outputFile = new RegularOutputFile(outputDirectory.resolve("output.tmp"));
        return this;
    }

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public ExportOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public boolean isUseAbsoluteResourcePaths() {
        return useAbsoluteResourcePaths;
    }

    public ExportOptions setUseAbsoluteResourcePaths(boolean useAbsoluteResourcePaths) {
        this.useAbsoluteResourcePaths = useAbsoluteResourcePaths;
        return this;
    }

    public Optional<SrsReference> getTargetSrs() {
        return Optional.ofNullable(targetSrs);
    }

    public ExportOptions setTargetSrs(SrsReference targetSrs) {
        this.targetSrs = targetSrs;
        return this;
    }

    public Optional<Matrix3x4> getAffineTransform() {
        return Optional.ofNullable(affineTransform);
    }

    public ExportOptions setAffineTransform(Matrix3x4 affineTransform) {
        this.affineTransform = affineTransform;
        return this;
    }

    public Optional<ValidityOptions> getValidityOptions() {
        return Optional.ofNullable(validityOptions);
    }

    public ExportOptions setValidityOptions(ValidityOptions validityOptions) {
        this.validityOptions = validityOptions;
        return this;
    }

    public Optional<LodOptions> getLodOptions() {
        return Optional.ofNullable(lodOptions);
    }

    public ExportOptions setLodOptions(LodOptions lodOptions) {
        this.lodOptions = lodOptions;
        return this;
    }

    public Optional<AppearanceOptions> getAppearanceOptions() {
        return Optional.ofNullable(appearanceOptions);
    }

    public ExportOptions setAppearanceOptions(AppearanceOptions appearanceOptions) {
        this.appearanceOptions = appearanceOptions;
        return this;
    }
}
