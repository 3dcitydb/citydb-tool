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

package org.citydb.io.citygml.writer;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.io.writer.options.OutputFormatOptions;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citygml4j.cityjson.adapter.appearance.serializer.AppearanceSerializer;
import org.citygml4j.cityjson.adapter.geometry.serializer.GeometrySerializer;
import org.citygml4j.cityjson.model.CityJSONVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SerializableConfig(name = "CityJSON")
public class CityJSONFormatOptions implements OutputFormatOptions {
    public static final String TEMPLATE_LOD_PROPERTY = "lod";

    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private CityJSONVersion version = CityJSONVersion.v2_0;
    private boolean prettyPrint;
    private boolean jsonLines = true;
    private boolean htmlSafe;
    private int vertexPrecision = GeometrySerializer.DEFAULT_VERTEX_PRECISION;
    private int templatePrecision = GeometrySerializer.DEFAULT_TEMPLATE_PRECISION;
    private int textureVertexPrecision = AppearanceSerializer.DEFAULT_TEXTURE_VERTEX_PRECISION;
    private boolean transformCoordinates = true;
    private boolean replaceTemplateGeometries;
    private boolean useMaterialDefaults = true;
    private String fallbackTheme = AppearanceSerializer.FALLBACK_THEME;
    private boolean useLod4AsLod3;
    private boolean writeGenericAttributeTypes;

    @JSONField(serialize = false, deserialize = false)
    private final Map<String, ImplicitGeometry> globalTemplates = new ConcurrentHashMap<>();

    public CityJSONVersion getVersion() {
        return version != null ? version : CityJSONVersion.v2_0;
    }

    public CityJSONFormatOptions setVersion(CityJSONVersion version) {
        this.version = version;
        return this;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public CityJSONFormatOptions setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
        return this;
    }

    public boolean isJsonLines() {
        return jsonLines;
    }

    public CityJSONFormatOptions setJsonLines(boolean jsonLines) {
        this.jsonLines = jsonLines;
        return this;
    }

    public boolean isHtmlSafe() {
        return htmlSafe;
    }

    public CityJSONFormatOptions setHtmlSafe(boolean htmlSafe) {
        this.htmlSafe = htmlSafe;
        return this;
    }

    public int getVertexPrecision() {
        return vertexPrecision;
    }

    public CityJSONFormatOptions setVertexPrecision(int vertexPrecision) {
        this.vertexPrecision = vertexPrecision;
        return this;
    }

    public int getTemplatePrecision() {
        return templatePrecision;
    }

    public CityJSONFormatOptions setTemplatePrecision(int templatePrecision) {
        this.templatePrecision = templatePrecision;
        return this;
    }

    public int getTextureVertexPrecision() {
        return textureVertexPrecision;
    }

    public CityJSONFormatOptions setTextureVertexPrecision(int textureVertexPrecision) {
        this.textureVertexPrecision = textureVertexPrecision;
        return this;
    }

    public boolean isTransformCoordinates() {
        return transformCoordinates;
    }

    public CityJSONFormatOptions setTransformCoordinates(boolean transformCoordinates) {
        this.transformCoordinates = transformCoordinates;
        return this;
    }

    public boolean isReplaceTemplateGeometries() {
        return replaceTemplateGeometries;
    }

    public CityJSONFormatOptions setReplaceTemplateGeometries(boolean replaceTemplateGeometries) {
        this.replaceTemplateGeometries = replaceTemplateGeometries;
        return this;
    }

    public boolean isUseMaterialDefaults() {
        return useMaterialDefaults;
    }

    public CityJSONFormatOptions setUseMaterialDefaults(boolean useMaterialDefaults) {
        this.useMaterialDefaults = useMaterialDefaults;
        return this;
    }

    public String getFallbackTheme() {
        return fallbackTheme != null ? fallbackTheme : AppearanceSerializer.FALLBACK_THEME;
    }

    public CityJSONFormatOptions setFallbackTheme(String fallbackTheme) {
        this.fallbackTheme = fallbackTheme;
        return this;
    }

    public boolean isUseLod4AsLod3() {
        return useLod4AsLod3;
    }

    public CityJSONFormatOptions setUseLod4AsLod3(boolean useLod4AsLod3) {
        this.useLod4AsLod3 = useLod4AsLod3;
        return this;
    }

    public boolean isWriteGenericAttributeTypes() {
        return writeGenericAttributeTypes;
    }

    public CityJSONFormatOptions setWriteGenericAttributeTypes(boolean writeGenericAttributeTypes) {
        this.writeGenericAttributeTypes = writeGenericAttributeTypes;
        return this;
    }

    public Map<String, ImplicitGeometry> getGlobalTemplates() {
        return globalTemplates;
    }

    public CityJSONFormatOptions addGlobalTemplate(ImplicitGeometry template, String lodValue) {
        if (template != null) {
            Number lod = 0;
            if (lodValue != null) {
                try {
                    lod = Double.parseDouble(lodValue);
                    if (lod.doubleValue() == lod.intValue()) {
                        lod = lod.intValue();
                    }
                } catch (NumberFormatException e) {
                    //
                }
            }

            template.getUserProperties().set(TEMPLATE_LOD_PROPERTY, lod);
            globalTemplates.put(template.getOrCreateObjectId(), template);
        }

        return this;
    }

    public List<ImplicitGeometry> consumeGlobalTemplates() {
        List<ImplicitGeometry> templates = new ArrayList<>(globalTemplates.values());
        globalTemplates.clear();
        return templates;
    }
}
