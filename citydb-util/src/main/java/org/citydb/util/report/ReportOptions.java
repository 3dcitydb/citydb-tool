/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.report;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.util.report.options.FeatureScope;

@SerializableConfig(name = "reportOptions")
public class ReportOptions {
    private int numberOfThreads;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private FeatureScope featureScope = FeatureScope.ALL;
    private boolean compact;
    private boolean includeGenericAttributes;
    private boolean includeDatabaseSize;

    public int getNumberOfThreads() {
        return numberOfThreads;
    }

    public ReportOptions setNumberOfThreads(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        return this;
    }

    public FeatureScope getFeatureScope() {
        return featureScope != null ? featureScope : FeatureScope.ALL;
    }

    public ReportOptions setFeatureScope(FeatureScope featureScope) {
        this.featureScope = featureScope;
        return this;
    }

    public boolean isCompact() {
        return compact;
    }

    public ReportOptions setCompact(boolean compact) {
        this.compact = compact;
        return this;
    }

    public boolean isIncludeGenericAttributes() {
        return includeGenericAttributes;
    }

    public ReportOptions setIncludeGenericAttributes(boolean includeGenericAttributes) {
        this.includeGenericAttributes = includeGenericAttributes;
        return this;
    }

    public boolean isIncludeDatabaseSize() {
        return includeDatabaseSize;
    }

    public ReportOptions setIncludeDatabaseSize(boolean includeDatabaseSize) {
        this.includeDatabaseSize = includeDatabaseSize;
        return this;
    }
}
