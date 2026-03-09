/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.bridge;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.bridge.BridgePart;

@DatabaseType(name = "BridgePart", namespace = Namespaces.BRIDGE)
public class BridgePartAdapter extends AbstractBridgeAdapter<BridgePart> {

    @Override
    public Feature createModel(BridgePart source) throws ModelBuildException {
        return Feature.of(FeatureType.BRIDGE_PART);
    }

    @Override
    public BridgePart createObject(Feature source) throws ModelSerializeException {
        return new BridgePart();
    }
}
