/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.tunnel;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.tunnel.HollowSpaceProperty;

public class HollowSpacePropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<HollowSpaceProperty> {

    @Override
    public HollowSpaceProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new HollowSpaceProperty();
    }
}
