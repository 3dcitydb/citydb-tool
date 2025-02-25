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

package org.citydb.io.citygml.adapter.dynamizer;

import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citygml4j.core.model.dynamizer.StandardFileTimeseries;

@DatabaseType(name = "StandardFileTimeseries", namespace = Namespaces.DYNAMIZER)
public class StandardFileTimeseriesAdapter extends AbstractAtomicTimeseriesAdapter<StandardFileTimeseries> {

    @Override
    public Feature createModel(StandardFileTimeseries source) throws ModelBuildException {
        return Feature.of(FeatureType.STANDARD_FILE_TIMESERIES);
    }

    @Override
    public void build(StandardFileTimeseries source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getFileLocation() != null) {
            target.addProperty(Attribute.of(Name.of("fileLocation", Namespaces.DYNAMIZER), DataType.URI)
                    .setURI(source.getFileLocation()));
        }

        if (source.getFileType() != null) {
            helper.addAttribute(Name.of("fileType", Namespaces.DYNAMIZER), source.getFileType(), target,
                    CodeAdapter.class);
        }

        if (source.getMimeType() != null) {
            helper.addAttribute(Name.of("mimeType", Namespaces.DYNAMIZER), source.getMimeType(), target,
                    CodeAdapter.class);
        }
    }

    @Override
    public StandardFileTimeseries createObject(Feature source) throws ModelSerializeException {
        return new StandardFileTimeseries();
    }

    @Override
    public void serialize(Feature source, StandardFileTimeseries target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("fileLocation", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getURI)
                .ifPresent(target::setFileLocation);

        Attribute fileType = source.getAttributes()
                .getFirst(Name.of("fileType", Namespaces.DYNAMIZER))
                .orElse(null);
        if (fileType != null) {
            target.setFileType(helper.getAttribute(fileType, CodeAdapter.class));
        }

        Attribute mimeType = source.getAttributes()
                .getFirst(Name.of("mimeType", Namespaces.DYNAMIZER))
                .orElse(null);
        if (mimeType != null) {
            target.setMimeType(helper.getAttribute(mimeType, CodeAdapter.class));
        }
    }
}
