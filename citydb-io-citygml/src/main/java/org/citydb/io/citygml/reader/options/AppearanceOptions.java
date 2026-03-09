/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.options;

import java.util.HashSet;
import java.util.Set;

public class AppearanceOptions {
    private boolean readAppearances = true;
    private Set<String> themes;

    public boolean isReadAppearances() {
        return readAppearances;
    }

    public AppearanceOptions setReadAppearances(boolean readAppearances) {
        this.readAppearances = readAppearances;
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
