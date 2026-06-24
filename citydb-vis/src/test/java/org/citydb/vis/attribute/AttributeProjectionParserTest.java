/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.vis.attribute.AttributeProjection.AddressSource;
import org.citydb.vis.attribute.AttributeProjection.AttributesSource;
import org.citydb.vis.attribute.AttributeProjection.FeatureSource;
import org.citydb.vis.attribute.AttributeProjection.Mapping;
import org.citydb.vis.attribute.AttributeProjection.PathSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Characterization tests for the {@code --attributes} grammar, exercised
 * through the public {@link AttributeProjection#parse} contract (which delegates
 * to the package-private parser). This grammar is the single source of truth
 * for projecting per-feature attributes onto the I3S / 3D Tiles attribute
 * table; a parsing bug silently drops or mis-types user intent rather than
 * failing loudly, so these tests lock the structural decisions (table
 * dispatch, aggregate / predicate / value-type peeling, string-aware
 * splitting) and the error surface against future refactors.
 */
class AttributeProjectionParserTest {

    private static Mapping parseOne(String token) {
        AttributeProjection p = AttributeProjection.parse(List.of(token));
        assertEquals(1, p.mappings().size());
        return p.mappings().get(0);
    }

    @Test
    void parsesFeatureSource() {
        Mapping m = parseOne("oid:FEATURE/objectid");
        assertEquals("oid", m.outputColumn());
        FeatureSource src = assertInstanceOf(FeatureSource.class, m.source());
        assertEquals(FeatureField.OBJECTID, src.field());
    }

    @Test
    void parsesAttributesSourceWithAggregatePredicatesAndCast() {
        // One mapping that exercises every ATTRIBUTES decision point at once:
        // explicit aggregate, a nested dotted path, a predicate of each value
        // type, a namespace-prefixed segment, and a trailing value-type cast.
        Mapping m = parseOne(
                "val:ATTRIBUTES/[ALL]bldg:a[s='munich'].b[n=5].c[d=1.5].leaf[flag=true]::uri");
        AttributesSource src = assertInstanceOf(AttributesSource.class, m.source());

        assertEquals(AttributeProjection.Aggregate.ALL, src.aggregate());
        assertEquals(ValueType.URI, src.valueType());

        List<PathSegment> path = src.path();
        assertEquals(4, path.size());
        // Namespace prefix is stripped to the local name.
        assertEquals("a", path.get(0).localName());

        // Predicate value typing: string / long / double / boolean.
        assertEquals("munich", path.get(0).predicate().value());
        assertEquals(5L, path.get(1).predicate().value());
        assertEquals(1.5, path.get(2).predicate().value());
        assertEquals(Boolean.TRUE, path.get(3).predicate().value());
    }

    @Test
    void defaultsAggregateToFirstAndIgnoresColonInsideQuotedPredicate() {
        // No leading [..] -> FIRST. The '::' lives inside a quoted predicate
        // value and must NOT be mistaken for a value-type cast marker.
        Mapping m = parseOne("note:ATTRIBUTES/a[note='x::y']");
        AttributesSource src = assertInstanceOf(AttributesSource.class, m.source());

        assertEquals(AttributeProjection.Aggregate.FIRST, src.aggregate());
        assertNull(src.valueType());
        assertEquals(1, src.path().size());
        assertEquals("x::y", src.path().get(0).predicate().value());
    }

    @Test
    void parsesAddressSourceWithAggregateAndPredicate() {
        Mapping m = parseOne("city:ADDRESS/[FIRST]street[city='Munich']");
        AddressSource src = assertInstanceOf(AddressSource.class, m.source());

        assertEquals(AddressField.STREET, src.field());
        assertEquals(AttributeProjection.Aggregate.FIRST, src.aggregate());
        assertEquals("city", src.predicate().field());
        assertEquals("Munich", src.predicate().value());
    }

    @Test
    void tableAndAggregateKeywordsAreCaseInsensitive() {
        AddressSource src = assertInstanceOf(AddressSource.class,
                parseOne("c:address/[last]city").source());
        assertEquals(AddressField.CITY, src.field());
        assertEquals(AttributeProjection.Aggregate.LAST, src.aggregate());
    }

    @Test
    void rejectsDuplicateOutputColumn() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AttributeProjection.parse(List.of("a:FEATURE/objectid", "a:FEATURE/identifier")));
        assertEquals(true, ex.getMessage().contains("duplicate output column"));
    }

    @Test
    void rejectsMalformedTokens() {
        // One representative per major error branch in the parser. All must
        // throw IllegalArgumentException carrying the "<location>: " prefix.
        String[] bad = {
                "",                                   // empty mapping
                "FEATURE/objectid",                   // missing ':'
                "a,b:FEATURE/objectid",               // reserved char in output column
                "x:NOPE/field",                       // unknown table
                "x:FEATURE/[FIRST]objectid",          // FEATURE forbids aggregate
                "x:FEATURE/a.b",                      // FEATURE forbids dotted path
                "x:FEATURE/bogus",                    // unknown FEATURE field
                "x:ATTRIBUTES/",                      // empty ATTRIBUTES path
                "x:ATTRIBUTES/a[k=1",                 // unterminated '['
                "x:ATTRIBUTES/a]b",                   // unmatched ']'
                "x:ATTRIBUTES/a[k='oops]",            // unterminated string literal
                "x:ATTRIBUTES/a::bogus",              // unknown value-type cast
                "x:ATTRIBUTES/a::",                   // empty value-type cast
                "x:ADDRESS/street::uri",              // ADDRESS forbids cast
                "x:ADDRESS/bogus",                    // unknown ADDRESS field
                "x:ADDRESS/street[bogus='v']",        // unknown ADDRESS predicate field
                "x:ATTRIBUTES/[BOGUS]a",              // unknown aggregate
                "x:ATTRIBUTES/a[novalue]",            // predicate not field=value
        };
        for (String token : bad) {
            assertThrows(IllegalArgumentException.class,
                    () -> AttributeProjection.parse(List.of(token)),
                    "expected parse failure for: '" + token + "'");
        }
    }
}
