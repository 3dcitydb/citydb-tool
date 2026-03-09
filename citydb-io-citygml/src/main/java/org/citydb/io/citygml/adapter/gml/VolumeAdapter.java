/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.gml;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.Attribute;
import org.xmlobjects.gml.model.measures.Volume;

public class VolumeAdapter extends AbstractMeasureAdapter<Volume> {

    @Override
    public Volume createObject(Attribute source) throws ModelSerializeException {
        return new Volume();
    }
}
