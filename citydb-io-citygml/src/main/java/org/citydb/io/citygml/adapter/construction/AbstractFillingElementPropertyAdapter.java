/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.construction.AbstractFillingElementProperty;

public class AbstractFillingElementPropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<AbstractFillingElementProperty> {

    @Override
    public AbstractFillingElementProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new AbstractFillingElementProperty();
    }
}
