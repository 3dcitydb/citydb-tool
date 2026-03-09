/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.building;

import org.citydb.io.citygml.adapter.construction.AbstractConstructiveElementAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.building.BuildingConstructiveElement;

@DatabaseType(name = "BuildingConstructiveElement", namespace = Namespaces.BUILDING)
public class BuildingConstructiveElementAdapter extends AbstractConstructiveElementAdapter<BuildingConstructiveElement> {

    @Override
    public Feature createModel(BuildingConstructiveElement source) throws ModelBuildException {
        return Feature.of(FeatureType.BUILDING_CONSTRUCTIVE_ELEMENT);
    }

    @Override
    public void build(BuildingConstructiveElement source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);
    }

    @Override
    public BuildingConstructiveElement createObject(Feature source) throws ModelSerializeException {
        return new BuildingConstructiveElement();
    }

    @Override
    public void serialize(Feature source, BuildingConstructiveElement target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.BUILDING);
    }
}
