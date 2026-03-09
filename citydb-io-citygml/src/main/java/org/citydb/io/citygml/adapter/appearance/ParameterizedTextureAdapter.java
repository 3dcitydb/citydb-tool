/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.appearance;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.common.Namespaces;

@DatabaseType(name = "ParameterizedTexture", namespace = Namespaces.APPEARANCE)
public class ParameterizedTextureAdapter extends TextureAdapter<ParameterizedTexture, org.citygml4j.core.model.appearance.ParameterizedTexture> {

    @Override
    public ParameterizedTexture createModel(org.citygml4j.core.model.appearance.ParameterizedTexture source) {
        return ParameterizedTexture.newInstance();
    }

    @Override
    public org.citygml4j.core.model.appearance.ParameterizedTexture createObject(ParameterizedTexture source) {
        return new org.citygml4j.core.model.appearance.ParameterizedTexture();
    }
}
