/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.json;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class JsonMapping {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(visibility -> visibility
                    .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                    .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .changeDefaultPropertyInclusion(inclusion -> inclusion
                    .withValueInclusion(JsonInclude.Include.NON_NULL)
                    .withContentInclusion(JsonInclude.Include.NON_NULL))
            .changeDefaultNullHandling(handling -> JsonSetter.Value.forValueNulls(Nulls.SKIP))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(JsonNodeFeature.WRITE_NULL_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    private static final JsonMapper PRETTY_MAPPER = MAPPER.rebuild()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private JsonMapping() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static JsonMapper prettyMapper() {
        return PRETTY_MAPPER;
    }
}
