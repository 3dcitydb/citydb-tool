/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.building.StoreyProperty;

public class StoreyPropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<StoreyProperty> {

    @Override
    public StoreyProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new StoreyProperty();
    }
}
