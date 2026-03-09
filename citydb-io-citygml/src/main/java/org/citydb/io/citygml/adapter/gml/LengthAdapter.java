/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.gml;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.Attribute;
import org.xmlobjects.gml.model.measures.Length;

public class LengthAdapter extends AbstractMeasureAdapter<Length> {

    @Override
    public Length createObject(Attribute source) throws ModelSerializeException {
        return new Length();
    }
}
