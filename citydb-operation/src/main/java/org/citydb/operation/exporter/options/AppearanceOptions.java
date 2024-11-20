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

package org.citydb.operation.exporter.options;

import java.util.HashSet;
import java.util.Set;

public class AppearanceOptions {
    private boolean exportAppearances = true;
    private int numberOfTextureBuckets;
    private Set<String> themes;

    public boolean isExportAppearances() {
        return exportAppearances;
    }

    public AppearanceOptions setExportAppearances(boolean exportAppearances) {
        this.exportAppearances = exportAppearances;
        return this;
    }

    public int getNumberOfTextureBuckets() {
        return numberOfTextureBuckets;
    }

    public AppearanceOptions setNumberOfTextureBuckets(int numberOfTextureBuckets) {
        this.numberOfTextureBuckets = numberOfTextureBuckets;
        return this;
    }

    public boolean hasThemes() {
        return themes != null && !themes.isEmpty();
    }

    public Set<String> getThemes() {
        if (themes == null) {
            themes = new HashSet<>();
        }

        return themes;
    }

    public AppearanceOptions setThemes(Set<String> themes) {
        this.themes = themes;
        return this;
    }

    public AppearanceOptions addTheme(String theme) {
        getThemes().add(theme);
        return this;
    }

    public AppearanceOptions addNullTheme() {
        return addTheme(null);
    }
}
