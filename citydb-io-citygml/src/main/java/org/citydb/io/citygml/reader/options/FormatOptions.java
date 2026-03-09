/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.options;

import org.citydb.io.reader.options.InputFormatOptions;

import java.util.Optional;

public abstract class FormatOptions<T extends FormatOptions<T>> implements InputFormatOptions {
    private AppearanceOptions appearanceOptions;

    protected abstract T self();

    public Optional<AppearanceOptions> getAppearanceOptions() {
        return Optional.ofNullable(appearanceOptions);
    }

    public T setAppearanceOptions(AppearanceOptions appearanceOptions) {
        this.appearanceOptions = appearanceOptions;
        return self();
    }
}
