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

package org.citydb.io.citygml.adapter.relief;

import org.citydb.io.citygml.adapter.core.AbstractPointCloudPropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiPointPropertyAdapter;
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
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.relief.MassPointRelief;

@DatabaseType(name = "MassPointRelief", namespace = Namespaces.RELIEF)
public class MassPointReliefAdapter extends AbstractReliefComponentAdapter<MassPointRelief> {

    @Override
    public Feature createModel(MassPointRelief source) throws ModelBuildException {
        return Feature.of(FeatureType.MASS_POINT_RELIEF);
    }

    @Override
    public void build(MassPointRelief source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getReliefPoints() != null) {
            helper.addPointGeometry(Name.of("reliefPoints", Namespaces.RELIEF), source.getReliefPoints(),
                    Lod.of(source.getLod()), target);
        }

        if (source.getPointCloud() != null) {
            helper.addContainedFeature(Name.of("pointCloud", Namespaces.RELIEF), source.getPointCloud(), target);
        }
    }

    @Override
    public MassPointRelief createObject(Feature source) throws ModelSerializeException {
        return new MassPointRelief();
    }

    @Override
    public void serialize(Feature source, MassPointRelief target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        GeometryProperty reliefPoints = source.getGeometries()
                .getFirst(Name.of("reliefPoints", Namespaces.CORE))
                .orElse(null);
        if (reliefPoints != null) {
            target.setReliefPoints(helper.getGeometryProperty(reliefPoints, MultiPointPropertyAdapter.class));
        }

        FeatureProperty pointCloud = source.getFeatures()
                .getFirst(Name.of("pointCloud", Namespaces.RELIEF))
                .orElse(null);
        if (pointCloud != null) {
            target.setPointCloud(helper.getObjectProperty(pointCloud, AbstractPointCloudPropertyAdapter.class));
        }
    }
}
