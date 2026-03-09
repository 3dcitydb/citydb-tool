/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.transportation.Square;

@DatabaseType(name = "Square", namespace = Namespaces.TRANSPORTATION)
public class SquareAdapter extends AbstractTransportationSpaceAdapter<Square> {

    @Override
    public Feature createModel(Square source) throws ModelBuildException {
        return Feature.of(FeatureType.SQUARE);
    }

    @Override
    public void build(Square source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);
    }

    @Override
    public Square createObject(Feature source) throws ModelSerializeException {
        return new Square();
    }

    @Override
    public void serialize(Feature source, Square target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);
    }
}
