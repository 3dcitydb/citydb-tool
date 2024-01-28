/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.citygml.adapter.bridge;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.FeatureProperty;
import org.citygml4j.core.model.bridge.Bridge;
import org.citygml4j.core.model.bridge.BridgePartProperty;

@DatabaseType(name = "Bridge", namespace = Namespaces.BRIDGE)
public class BridgeAdapter extends AbstractBridgeAdapter<Bridge> {

    @Override
    public Feature createModel(Bridge source) throws ModelBuildException {
        return Feature.of(FeatureType.BRIDGE);
    }

    @Override
    public void build(Bridge source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetBridgeParts()) {
            for (BridgePartProperty property : source.getBridgeParts()) {
                helper.addContainedFeature(Name.of("bridgePart", Namespaces.BRIDGE), property, target);
            }
        }
    }

    @Override
    public Bridge createObject(Feature source) throws ModelSerializeException {
        return new Bridge();
    }

    @Override
    public void serialize(Feature source, Bridge target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (FeatureProperty property : source.getFeatures().get(Name.of("bridgePart", Namespaces.BRIDGE))) {
            target.getBridgeParts().add(helper.getObjectProperty(property, BridgePartPropertyAdapter.class));
        }
    }
}
