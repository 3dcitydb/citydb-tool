/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.relief;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.relief.AbstractReliefComponentProperty;

public class AbstractReliefComponentPropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<AbstractReliefComponentProperty> {

    @Override
    public AbstractReliefComponentProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new AbstractReliefComponentProperty();
    }
}
