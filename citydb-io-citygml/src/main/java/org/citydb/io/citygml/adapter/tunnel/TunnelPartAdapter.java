/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.tunnel;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.tunnel.TunnelPart;

@DatabaseType(name = "TunnelPart", namespace = Namespaces.TUNNEL)
public class TunnelPartAdapter extends AbstractTunnelAdapter<TunnelPart> {

    @Override
    public Feature createModel(TunnelPart source) throws ModelBuildException {
        return Feature.of(FeatureType.TUNNEL_PART);
    }

    @Override
    public TunnelPart createObject(Feature source) throws ModelSerializeException {
        return new TunnelPart();
    }
}
