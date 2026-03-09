/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.transportation.ClearanceSpaceProperty;

public class ClearanceSpacePropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<ClearanceSpaceProperty> {

    @Override
    public ClearanceSpaceProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new ClearanceSpaceProperty();
    }
}
