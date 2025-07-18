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
