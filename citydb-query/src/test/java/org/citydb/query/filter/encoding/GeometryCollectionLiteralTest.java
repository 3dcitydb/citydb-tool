/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import com.alibaba.fastjson2.JSONWriter;
import org.citydb.model.geometry.LineString;
import org.citydb.model.geometry.Point;
import org.citydb.model.geometry.Polygon;
import org.citydb.query.filter.Filter;
import org.citydb.query.filter.literal.GeometryCollectionLiteral;
import org.citydb.query.filter.operation.BinarySpatialPredicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeometryCollectionLiteralTest {
    private static final String TEXT = "s_intersects(geometry, " +
            "GEOMETRYCOLLECTION(POINT(1 2), LINESTRING(0 0, 1 1), POLYGON((0 0, 1 0, 1 1, 0 0))))";
    private static final String JSON = "{\"op\": \"s_intersects\", \"args\": [{\"property\": \"geometry\"}, " +
            "{\"type\": \"GeometryCollection\", \"geometries\": [" +
            "{\"type\": \"Point\", \"coordinates\": [1, 2]}, " +
            "{\"type\": \"LineString\", \"coordinates\": [[0, 0], [1, 1]]}, " +
            "{\"type\": \"Polygon\", \"coordinates\": [[[0, 0], [1, 0], [1, 1], [0, 0]]]}]}]}";

    @Test
    void parsesText() throws FilterParseException {
        assertMixedCollection(Filter.ofText(TEXT));
    }

    @Test
    void parsesJson() throws FilterParseException {
        assertMixedCollection(Filter.ofJSON(JSON));
    }

    @Test
    void roundTripsText() throws FilterParseException {
        String text = FilterTextWriter.newInstance().write(Filter.ofText(TEXT).getExpression());
        assertTrue(text.contains("GEOMETRYCOLLECTION("), text);
        assertMixedCollection(Filter.ofText(text));
    }

    @Test
    void roundTripsJson() throws FilterParseException {
        try (JSONWriter jsonWriter = JSONWriter.of()) {
            FilterJSONWriter.newInstance().write(Filter.ofJSON(JSON).getExpression(), jsonWriter);
            assertMixedCollection(Filter.ofJSON(jsonWriter.toString()));
        }
    }

    @Test
    void rejectsNestedJsonCollections() {
        String json = "{\"op\": \"s_intersects\", \"args\": [{\"property\": \"geometry\"}, " +
                "{\"type\": \"GeometryCollection\", \"geometries\": [" +
                "{\"type\": \"GeometryCollection\", \"geometries\": [" +
                "{\"type\": \"Point\", \"coordinates\": [1, 2]}]}]}]}";
        assertThrows(FilterParseException.class, () -> Filter.ofJSON(json));
    }

    @Test
    void parsesTextWithZMembers() throws FilterParseException {
        Filter filter = Filter.ofText("s_intersects(geometry, " +
                "GEOMETRYCOLLECTION Z (POINT Z (1 2 3), LINESTRING Z (0 0 0, 1 1 1)))");
        BinarySpatialPredicate predicate = assertInstanceOf(BinarySpatialPredicate.class, filter.getExpression());
        GeometryCollectionLiteral literal = assertInstanceOf(GeometryCollectionLiteral.class,
                predicate.getRightOperand());
        assertEquals(2, literal.getValue().size());
        assertTrue(literal.getValue().stream().allMatch(member -> member.getValue().getVertexDimension() == 3));
    }

    @Test
    void rejectsPolyhedralSurfaceMembers() {
        assertRejected("GEOMETRYCOLLECTION(POLYHEDRALSURFACE Z (((0 0 0, 1 0 0, 1 1 0, 0 0 0))))");
    }

    @Test
    void rejectsNestedTextCollections() {
        assertRejected("GEOMETRYCOLLECTION(GEOMETRYCOLLECTION(POINT(1 2)))");
    }

    @Test
    void rejectsBBoxMembers() {
        assertRejected("GEOMETRYCOLLECTION(BBOX(0, 0, 1, 1))");
    }

    @Test
    void rejectsEmptyTextCollections() {
        assertRejected("GEOMETRYCOLLECTION EMPTY");
    }

    private void assertRejected(String collection) {
        assertThrows(FilterParseException.class, () -> Filter.ofText("s_intersects(geometry, " + collection + ")"));
    }

    private void assertMixedCollection(Filter filter) {
        BinarySpatialPredicate predicate = assertInstanceOf(BinarySpatialPredicate.class, filter.getExpression());
        GeometryCollectionLiteral literal = assertInstanceOf(GeometryCollectionLiteral.class,
                predicate.getRightOperand());
        assertEquals(3, literal.getValue().size());
        assertInstanceOf(Point.class, literal.getValue().get(0).getValue());
        assertInstanceOf(LineString.class, literal.getValue().get(1).getValue());
        assertInstanceOf(Polygon.class, literal.getValue().get(2).getValue());
    }
}
