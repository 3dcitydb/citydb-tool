/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.construction.Window;

@DatabaseType(name = "Window", namespace = Namespaces.CONSTRUCTION)
public class WindowAdapter extends AbstractFillingElementAdapter<Window> {

    @Override
    public Feature createModel(Window source) throws ModelBuildException {
        return Feature.of(FeatureType.WINDOW);
    }

    @Override
    public void build(Window source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.CONSTRUCTION);
    }

    @Override
    public Window createObject(Feature source) throws ModelSerializeException {
        return new Window();
    }

    @Override
    public void serialize(Feature source, Window target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.CONSTRUCTION);
    }
}
