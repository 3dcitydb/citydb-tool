/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.json;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.node.ObjectNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

public class JsonMappingTest {

    public enum Mode {
        IMPORT_ALL("importAll"),
        SKIP_EXISTING("skipExisting");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static class Options {
        private Mode mode;
        private OffsetDateTime date;
        private int threads = 7;
        private boolean flag = true;
    }

    @Test
    public void enumsAreReadIgnoringCase() {
        for (String value : new String[]{"skipExisting", "SKIPEXISTING", "skipexisting", "SkipExisting"}) {
            assertEquals(Mode.SKIP_EXISTING, read("{\"mode\": \"" + value + "\"}").mode, value);
        }
    }

    @Test
    public void enumConstantNamesAreRejected() {
        assertThrows(JacksonException.class, () -> read("{\"mode\": \"SKIP_EXISTING\"}"));
    }

    @Test
    public void enumsAreWrittenUsingToString() {
        Options options = new Options();
        options.mode = Mode.SKIP_EXISTING;
        assertTrue(JsonMapping.mapper().writeValueAsString(options).contains("\"mode\":\"skipExisting\""));
    }

    @Test
    public void unknownEnumValueFails() {
        assertThrows(JacksonException.class, () -> read("{\"mode\": \"nonsense\"}"));
    }

    @Test
    public void dateTimesKeepTheirOffsetByDefault() {
        OffsetDateTime date = read("{\"date\": \"2025-01-02T03:04:05+02:00\"}").date;
        assertEquals(OffsetDateTime.of(2025, 1, 2, 3, 4, 5, 0, ZoneOffset.ofHours(2)), date);
        assertEquals(ZoneOffset.ofHours(2), date.getOffset());
        assertThrows(JacksonException.class, () -> read("{\"date\": \"2025-01-02\"}"));
    }

    @Test
    public void nullKeepsTheDefault() {
        Options options = read("{\"threads\": null, \"flag\": null, \"mode\": null}");
        assertEquals(7, options.threads);
        assertTrue(options.flag);

        Options withDefault = new Options();
        withDefault.mode = Mode.IMPORT_ALL;
        assertEquals(Mode.IMPORT_ALL, JsonMapping.mapper().readerForUpdating(withDefault)
                .<Options>readValue("{\"mode\": null}").mode);
    }

    @Test
    public void unknownPropertiesAreIgnored() {
        Options options = read("{\"numberOfThread\": 4, \"nested\": {\"a\": [1, 2]}, \"threads\": 3}");
        assertEquals(3, options.threads);
    }

    @Test
    public void nullPropertiesAreNotWrittenForTrees() {
        ObjectNode node = JsonMapping.mapper().createObjectNode()
                .put("name", "x")
                .put("schema", (String) null);
        assertEquals("{\"name\":\"x\"}", JsonMapping.mapper().writeValueAsString(node));
    }

    private Options read(String json) {
        return JsonMapping.mapper().readValue(json, Options.class);
    }
}
