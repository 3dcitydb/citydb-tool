/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.core.json.JsonMapping;
import org.citydb.query.filter.Filter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class FilterConfigTest {
    private final JsonMapper mapper = JsonMapping.mapper();

    @Test
    void readsTextAndWritesJson() {
        Holder holder = mapper.readValue("{\"filter\": \"height > 10.5 and name = 'x'\"}", Holder.class);
        assertNotNull(holder.filter);

        JsonNode expected = mapper.readTree("{\"filter\": {\"op\": \"and\", \"args\": [" +
                "{\"op\": \">\", \"args\": [{\"property\": \"height\"}, 10.5]}, " +
                "{\"op\": \"=\", \"args\": [{\"property\": \"name\"}, \"x\"]}]}}");
        assertEquals(expected, mapper.readTree(mapper.writeValueAsString(holder)));
    }

    @Test
    void roundTripsJson() {
        String json = "{\"filter\":{\"op\":\"s_intersects\",\"args\":[{\"property\":\"geometry\"}," +
                "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]]}]}}";
        Holder holder = mapper.readValue(json, Holder.class);
        assertEquals(json, mapper.writeValueAsString(holder));
    }

    @Test
    void rejectsInvalidFilters() {
        DatabindException e = assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"filter\": \"height >\"}", Holder.class));
        assertTrue(e.getMessage().contains("height >"), e.getMessage());
        assertThrows(DatabindException.class,
                () -> mapper.readValue("{\"filter\": {\"op\": \"unknown_op\", \"args\": []}}", Holder.class));
        assertThrows(DatabindException.class, () -> mapper.readValue("{\"filter\": 42}", Holder.class));
    }

    static class Holder {
        @JsonSerialize(using = FilterConfigWriter.class)
        @JsonDeserialize(using = FilterConfigReader.class)
        Filter filter;
    }
}
