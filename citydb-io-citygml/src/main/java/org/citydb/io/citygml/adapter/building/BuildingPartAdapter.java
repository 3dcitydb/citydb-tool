/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.building.BuildingPart;

@DatabaseType(name = "BuildingPart", namespace = Namespaces.BUILDING)
public class BuildingPartAdapter extends AbstractBuildingAdapter<BuildingPart> {

    @Override
    public Feature createModel(BuildingPart source) throws ModelBuildException {
        return Feature.of(FeatureType.BUILDING_PART);
    }

    @Override
    public BuildingPart createObject(Feature source) throws ModelSerializeException {
        return new BuildingPart();
    }
}
