/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
