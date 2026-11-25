/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.citydb.config.SerializableConfig;
import org.citydb.config.common.SrsReference;
import org.citydb.core.concurrent.LazyCheckedInitializer;
import org.citydb.core.file.OutputFile;
import org.citydb.core.file.output.RegularOutputFile;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.encoding.Matrix3x4Reader;
import org.citydb.model.encoding.Matrix3x4Writer;
import org.citydb.model.feature.Validity;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.operation.exporter.options.ExternalFileResolver;
import org.citydb.operation.exporter.options.ImplicitGeometryScope;
import org.citydb.operation.exporter.options.LodOptions;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@SerializableConfig(name = "exportOptions")
public class ExportOptions {
    @JsonIgnore
    private final LazyCheckedInitializer<OutputFile, IOException> tempOutputFile = LazyCheckedInitializer.of(
            () -> new RegularOutputFile(Files.createTempDirectory("citydb-").resolve("output.tmp")));

    private int numberOfThreads;
    private boolean useAbsoluteResourcePaths;
    private SrsReference targetSrs;
    @JsonSerialize(using = Matrix3x4Writer.class)
    @JsonDeserialize(using = Matrix3x4Reader.class)
    private Matrix3x4 affineTransform;
    private LodOptions lodOptions;
    private AppearanceOptions appearanceOptions;

    @JsonIgnore
    private Validity validity;
    @JsonIgnore
    private OutputFile outputFile;
    @JsonIgnore
    private ImplicitGeometryScope implicitGeometryScope;
    @JsonIgnore
    private ExternalFileResolver externalFileResolver;

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

    public Optional<Validity> getValidity() {
        return Optional.ofNullable(validity);
    }

    public ExportOptions setValidity(Validity validity) {
        this.validity = validity;
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

    public ImplicitGeometryScope getImplicitGeometryScope() {
        return implicitGeometryScope != null ? implicitGeometryScope : ImplicitGeometryScope.GLOBAL;
    }

    public ExportOptions setImplicitGeometryScope(ImplicitGeometryScope implicitGeometryScope) {
        this.implicitGeometryScope = implicitGeometryScope;
        return this;
    }

    public Optional<ExternalFileResolver> getExternalFileResolver() {
        return Optional.ofNullable(externalFileResolver);
    }

    public ExportOptions setExternalFileResolver(ExternalFileResolver externalFileResolver) {
        this.externalFileResolver = externalFileResolver;
        return this;
    }
}
