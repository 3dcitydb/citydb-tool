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
import org.citygml4j.core.model.dynamizer.TabulatedFileTimeseries;
import org.citygml4j.core.model.dynamizer.TimeseriesValue;

@DatabaseType(name = "TabulatedFileTimeseries", namespace = Namespaces.DYNAMIZER)
public class TabulatedFileTimeseriesAdapter extends AbstractAtomicTimeseriesAdapter<TabulatedFileTimeseries> {

    @Override
    public Feature createModel(TabulatedFileTimeseries source) throws ModelBuildException {
        return Feature.of(FeatureType.TABULATED_FILE_TIMESERIES);
    }

    @Override
    public void build(TabulatedFileTimeseries source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
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

        if (source.getValueType() != null) {
            target.addProperty(Attribute.of(Name.of("valueType", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getValueType().toValue()));
        }

        if (source.getNumberOfHeaderLines() != null) {
            target.addProperty(Attribute.of(Name.of("numberOfHeaderLines", Namespaces.DYNAMIZER), DataType.INTEGER)
                    .setIntValue(source.getNumberOfHeaderLines()));
        }

        if (source.getFieldSeparator() != null) {
            target.addProperty(Attribute.of(Name.of("fieldSeparator", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getFieldSeparator()));
        }

        if (source.getDecimalSymbol() != null) {
            target.addProperty(Attribute.of(Name.of("decimalSymbol", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getDecimalSymbol()));
        }

        if (source.getIdColumnNo() != null) {
            target.addProperty(Attribute.of(Name.of("idColumnNo", Namespaces.DYNAMIZER), DataType.INTEGER)
                    .setIntValue(source.getIdColumnNo()));
        }

        if (source.getIdColumnName() != null) {
            target.addProperty(Attribute.of(Name.of("idColumnName", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getIdColumnName()));
        }

        if (source.getIdValue() != null) {
            target.addProperty(Attribute.of(Name.of("idValue", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getIdValue()));
        }

        if (source.getTimeColumnNo() != null) {
            target.addProperty(Attribute.of(Name.of("timeColumnNo", Namespaces.DYNAMIZER), DataType.INTEGER)
                    .setIntValue(source.getTimeColumnNo()));
        }

        if (source.getTimeColumnName() != null) {
            target.addProperty(Attribute.of(Name.of("timeColumnName", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getTimeColumnName()));
        }

        if (source.getValueColumnNo() != null) {
            target.addProperty(Attribute.of(Name.of("valueColumnNo", Namespaces.DYNAMIZER), DataType.INTEGER)
                    .setIntValue(source.getValueColumnNo()));
        }

        if (source.getValueColumnName() != null) {
            target.addProperty(Attribute.of(Name.of("valueColumnName", Namespaces.DYNAMIZER), DataType.STRING)
                    .setStringValue(source.getValueColumnName()));
        }
    }

    @Override
    public TabulatedFileTimeseries createObject(Feature source) throws ModelSerializeException {
        return new TabulatedFileTimeseries();
    }

    @Override
    public void serialize(Feature source, TabulatedFileTimeseries target, ModelSerializerHelper helper) throws ModelSerializeException {
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

        source.getAttributes().getFirst(Name.of("valueType", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setValueType(TimeseriesValue.fromValue(value)));

        source.getAttributes().getFirst(Name.of("numberOfHeaderLines", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setNumberOfHeaderLines(value.intValue()));

        source.getAttributes().getFirst(Name.of("fieldSeparator", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setFieldSeparator);

        source.getAttributes().getFirst(Name.of("decimalSymbol", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setDecimalSymbol);

        source.getAttributes().getFirst(Name.of("idColumnNo", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setIdColumnNo(value.intValue()));

        source.getAttributes().getFirst(Name.of("idColumnName", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setIdColumnName);

        source.getAttributes().getFirst(Name.of("idValue", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setIdValue);

        source.getAttributes().getFirst(Name.of("timeColumnNo", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setTimeColumnNo(value.intValue()));

        source.getAttributes().getFirst(Name.of("timeColumnName", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setTimeColumnName);

        source.getAttributes().getFirst(Name.of("valueColumnNo", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getIntValue)
                .ifPresent(value -> target.setValueColumnNo(value.intValue()));

        source.getAttributes().getFirst(Name.of("valueColumnName", Namespaces.DYNAMIZER))
                .flatMap(Attribute::getStringValue)
                .ifPresent(target::setValueColumnName);
    }
}
