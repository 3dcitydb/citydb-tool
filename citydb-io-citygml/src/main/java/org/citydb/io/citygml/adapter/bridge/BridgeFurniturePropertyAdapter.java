/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.bridge;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.bridge.BridgeFurnitureProperty;

public class BridgeFurniturePropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<BridgeFurnitureProperty> {

    @Override
    public BridgeFurnitureProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new BridgeFurnitureProperty();
    }
}
