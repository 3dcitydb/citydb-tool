/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.dynamizer;

import org.citydb.io.citygml.adapter.gml.AbstractInlineOrByReferencePropertyAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.dynamizer.AbstractTimeseriesProperty;

public class AbstractTimeseriesPropertyAdapter extends AbstractInlineOrByReferencePropertyAdapter<AbstractTimeseriesProperty> {

    @Override
    public AbstractTimeseriesProperty createObject(FeatureProperty source) throws ModelSerializeException {
        return new AbstractTimeseriesProperty();
    }
}
