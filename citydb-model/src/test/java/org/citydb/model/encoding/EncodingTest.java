/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.encoding;

import org.citydb.core.json.JsonMapping;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.geometry.Point;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.annotation.JsonDeserialize;

import static org.junit.jupiter.api.Assertions.*;

public class EncodingTest {

    public static class Holder {
        @JsonDeserialize(using = Matrix3x4Reader.class)
        private Matrix3x4 matrix;
        @JsonDeserialize(using = PointReader.class)
        private Point point;
    }

    @Test
    public void matrixWithAtLeast12NumbersIsAccepted() {
        assertNotNull(read("{\"matrix\": [1,0,0,0, 0,1,0,0, 0,0,1,0]}").matrix);
        assertNotNull(read("{\"matrix\": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]}").matrix);
    }

    @Test
    public void invalidMatrixFails() {
        assertThrows(JacksonException.class, () -> read("{\"matrix\": [1,0,0,0, 0,1,0,0, 0,0,1]}"));
        assertThrows(JacksonException.class, () -> read("{\"matrix\": [1,0,0,0, 0,1,0,0, 0,0,1,\"x\"]}"));
    }

    @Test
    public void pointWithAtLeast2CoordinatesIsAccepted() {
        assertEquals(5, read("{\"point\": {\"coordinates\": [5, 6]}}").point.getCoordinate().getX());
        assertEquals(6, read("{\"point\": {\"coordinates\": [5, 6, 0]}}").point.getCoordinate().getY());
    }

    @Test
    public void invalidPointFails() {
        assertThrows(JacksonException.class, () -> read("{\"point\": {\"coordinates\": [5]}}"));
    }

    private Holder read(String json) {
        return JsonMapping.mapper().readValue(json, Holder.class);
    }
}
