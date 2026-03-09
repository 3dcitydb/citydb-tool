/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.gml.AbstractReferenceAdapter;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.transportation.TrafficSpaceReference;

public class TrafficSpaceReferenceAdapter extends AbstractReferenceAdapter<TrafficSpaceReference> {

    @Override
    public TrafficSpaceReference createObject(FeatureProperty source) throws ModelSerializeException {
        return new TrafficSpaceReference();
    }
}
