/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.io.citygml.adapter.geometry.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.property.GeometryProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiCurveProperty;

public class MultiCurvePropertyAdapter extends AbstractGeometryPropertyAdapter<MultiCurveProperty> {

    @Override
    public MultiCurveProperty createObject(GeometryProperty source) throws ModelSerializeException {
        return new MultiCurveProperty();
    }
}
