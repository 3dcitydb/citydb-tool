/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.core.AbstractPointCloudProperty;

public class AbstractPointCloudPropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<AbstractPointCloudProperty> {

    @Override
    public AbstractPointCloudProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new AbstractPointCloudProperty();
    }
}
