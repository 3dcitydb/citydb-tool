/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.transportation.SectionProperty;

public class SectionPropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<SectionProperty> {

    @Override
    public SectionProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new SectionProperty();
    }
}
