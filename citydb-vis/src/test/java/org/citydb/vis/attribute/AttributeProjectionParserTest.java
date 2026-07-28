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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

    /**
     * Every rejection branch of the grammar, one row each, pinned by the
     * distinguishing fragment of its message rather than just by exception
     * type. The type alone is not enough: every branch throws
     * {@link IllegalArgumentException}, so a token that silently starts
     * tripping an <i>earlier</i> guard still passes a type-only assertion
     * while its intended branch goes dead. Half the branches below were
     * unreachable from the previous type-only table for exactly that reason —
     * e.g. {@code a[k='oops]} never reached the unterminated-string check
     * because the unbalanced bracket fires first.
     * <p>
     * The message is also the user-facing product here: {@code --attributes} is
     * hand-written on the command line, so a wrong-but-plausible message costs
     * more than a stack trace.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("malformedTokens")
    void rejectsMalformedToken(String token, String expectedMessageFragment) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> AttributeProjection.parse(List.of(token)),
                "expected parse failure for: '" + token + "'");
        assertTrue(ex.getMessage().contains(expectedMessageFragment),
                "token '" + token + "' threw the wrong branch.\n  expected to contain: "
                        + expectedMessageFragment + "\n  actual: " + ex.getMessage());
        // Every message carries the "--attributes[i]: " location prefix so the
        // user can tell which of several tokens is at fault.
        assertTrue(ex.getMessage().startsWith("--attributes"),
                "message lost its location prefix: " + ex.getMessage());
    }

    static Stream<Arguments> malformedTokens() {
        return Stream.of(
                // ---- mapping shape ----
                arguments("", "mapping must not be empty"),
                arguments("FEATURE/objectid", "missing ':' separating output column"),
                arguments("a,b:FEATURE/objectid", "contains a reserved character"),
                arguments("x:FEATURE", "missing '/' separating table from column"),
                arguments("x:NOPE/field", "unknown table 'NOPE'"),

                // ---- FEATURE: single row, statically typed ----
                arguments("x:FEATURE/[FIRST]objectid", "must not declare an aggregate"),
                arguments("x:FEATURE/objectid[a=1]", "must not declare a predicate"),
                arguments("x:FEATURE/objectid::uri", "must not declare a value-type cast"),
                arguments("x:FEATURE/a.b", "FEATURE source must reference a single"),
                arguments("x:FEATURE/bogus", "FEATURE field 'bogus' is not"),

                // ---- ATTRIBUTES: path, segments, cast ----
                arguments("x:ATTRIBUTES/", "ATTRIBUTES source has an empty column path"),
                arguments("x:ATTRIBUTES/a[k=1", "unterminated '[' in path"),
                arguments("x:ATTRIBUTES/a]b", "unmatched ']' in path"),
                arguments("x:ATTRIBUTES/a'b", "unterminated string literal in path"),
                arguments("x:ATTRIBUTES/a..b", "ATTRIBUTES path has an empty segment"),
                arguments("x:ATTRIBUTES/a[k=1]b", "unexpected text after its predicate"),
                arguments("x:ATTRIBUTES/a.[k=1]", "has no localName before the predicate"),
                arguments("x:ATTRIBUTES/ns:", "missing a local name after the prefix"),
                arguments("x:ATTRIBUTES/a::bogus", "unknown value-type cast 'bogus'"),
                arguments("x:ATTRIBUTES/a::", "empty value-type cast after '::'"),

                // ---- ADDRESS: single row per address, statically typed ----
                arguments("x:ADDRESS/street::uri", "ADDRESS source must not declare a"),
                arguments("x:ADDRESS/a.b", "ADDRESS source must reference a single"),
                arguments("x:ADDRESS/bogus", "ADDRESS field 'bogus' is not"),
                arguments("x:ADDRESS/street[bogus='v']", "predicate field 'bogus'"),

                // ---- aggregate prefix ----
                arguments("x:ATTRIBUTES/[BOGUS]a", "unknown aggregate 'BOGUS'"),
                arguments("x:ADDRESS/[FIRST", "unterminated '[' in aggregate"),

                // ---- predicate suffix ----
                arguments("x:ADDRESS/street[a='v'", "unterminated '[' in predicate"),
                arguments("x:ADDRESS/street[a='v']z", "unexpected text after predicate"),
                arguments("x:ATTRIBUTES/a[novalue]", "is not of the form"),
                // A predicate whose field is only whitespace also lands on the
                // "not of the form field=value" branch, not on the dedicated
                // empty-field guard: both call sites trim the bracket contents
                // before parsing, so '=' can never sit at index >= 1 with
                // nothing but blanks in front of it. See the class javadoc note
                // on the two guards that are unreachable by construction.
                arguments("x:ATTRIBUTES/a[ =1]", "is not of the form"),
                arguments("x:ATTRIBUTES/a[k=]", "has an empty value"),
                arguments("x:ATTRIBUTES/a[k='v'x]", "is not terminated with a closing quote"),
                arguments("x:ATTRIBUTES/a[k=1.2.3]", "is not a quoted string, integer"));
    }

    /**
     * {@code parse} trims each token before handing it on, so an output column
     * that is blank rather than absent can only arrive through the
     * package-private entry point — the public path always trips the
     * missing-':' guard first. Pinned here so the guard is not mistaken for
     * live validation of CLI input.
     */
    @Test
    void blankOutputColumnIsRejectedOnlyBelowTheTrimmingEntryPoint() {
        assertThrows(IllegalArgumentException.class,
                () -> AttributeProjectionParser.parseMapping(" :FEATURE/objectid", "--attributes[0]"));

        IllegalArgumentException viaPublicPath = assertThrows(IllegalArgumentException.class,
                () -> AttributeProjection.parse(List.of(" :FEATURE/objectid")));
        assertTrue(viaPublicPath.getMessage().contains("missing ':' separating output column"),
                "the public path is expected to trim first and fail on the missing ':'.");
    }

    /**
     * The record invariants the parser is trusted to uphold. They are public
     * API — anything constructing a projection programmatically rather than
     * from the CLI grammar must hit the same guards.
     */
    @Test
    void sourceRecordsRejectStructurallyInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new AttributesSource(
                List.of(), AttributeProjection.Aggregate.FIRST, ValueType.STRING));
        assertThrows(IllegalArgumentException.class, () -> new PathSegment("", null));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeProjection.Predicate("field", new Object()));

        assertThrows(NullPointerException.class, () -> new PathSegment(null, null));
        assertThrows(NullPointerException.class,
                () -> new AttributeProjection.Predicate("field", null));
    }
}
