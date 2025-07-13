package org.citydb.util.report;

public class ReportOptions {
    private boolean onlyPropertiesOfValidFeatures;
    private boolean includeGenericAttributes;
    private boolean includeDatabaseSize;

    public static ReportOptions defaults() {
        return new ReportOptions();
    }

    public boolean isOnlyPropertiesOfValidFeatures() {
        return onlyPropertiesOfValidFeatures;
    }

    public ReportOptions onlyPropertiesOfValidFeatures(boolean onlyPropertiesOfValidFeatures) {
        this.onlyPropertiesOfValidFeatures = onlyPropertiesOfValidFeatures;
        return this;
    }

    public boolean isIncludeGenericAttributes() {
        return includeGenericAttributes;
    }

    public ReportOptions includeGenericAttributes(boolean includeGenericAttributes) {
        this.includeGenericAttributes = includeGenericAttributes;
        return this;
    }

    public boolean isIncludeDatabaseSize() {
        return includeDatabaseSize;
    }

    public ReportOptions includeDatabaseSize(boolean includeDatabaseSize) {
        this.includeDatabaseSize = includeDatabaseSize;
        return this;
    }
}
