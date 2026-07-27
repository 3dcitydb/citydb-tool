/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.model.address.Address;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.AddressProperty;
import org.citydb.model.property.ArrayValue;
import org.citydb.model.property.Attribute;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evaluation-side companion to {@link AttributeProjectionParserTest}: that test
 * pins the {@code --attributes} grammar, this one pins what the parsed
 * projection actually pulls off a {@code Feature}.
 * <p>
 * Every case is driven through the user-facing grammar
 * ({@link AttributeProjection#parse}) rather than hand-built {@code Source}
 * records, so parser and evaluator are exercised as the one contract the user
 * sees. The failure mode being guarded is silent: a wrong path walk, a
 * mis-scoped predicate or a dropped aggregate produces an empty or wrong
 * attribute cell in the exported I3S / 3D Tiles table, never an error.
 * <p>
 * Also covers the schema side — {@link AttributeEncoder#trackFieldTypes} plus
 * {@link AttributeEncoder#finalizeFields} — where the type-promotion rules
 * decide the binary layout both writers commit to.
 */
class AttributeEncoderTest {

    // ---- default extraction (no projection) --------------------------------

    @Test
    void defaultExtractionEmitsEveryTopLevelAttributeAutoDetected() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("storeys").setIntValue(4L))
                .addAttribute(attr("height").setDoubleValue(12.5))
                .addAttribute(attr("class").setStringValue("residential"))
                .addAttribute(attr("built").setTimeStamp(
                        OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)))
                .addAttribute(attr("ref").setURI("https://example.org/a"))
                .addAttribute(attr("tags").setArrayValue(ArrayValue.ofString(List.of("a", "b"))))
                // No value in any slot and no 'value' child: contributes nothing.
                .addAttribute(attr("empty"));

        Map<String, Object> values = new AttributeEncoder().extractAttributes(feature);

        assertEquals(4L, values.get("storeys"));
        assertEquals(12.5, values.get("height"));
        assertEquals("residential", values.get("class"));
        assertEquals("2020-01-02T03:04:05Z", values.get("built"));
        assertEquals("https://example.org/a", values.get("ref"));
        assertEquals("a; b", values.get("tags"));
        assertFalse(values.containsKey("empty"), "value-less attributes must be dropped, not null-mapped");
        assertEquals(6, values.size());
    }

    @Test
    void autoDetectionFollowsIntDoubleStringPriorityAndRecursesIntoValueChild() {
        Feature feature = Feature.of(Name.of("Building"))
                // All three slots populated at once — the documented priority
                // order (int -> double -> string) decides, not the setter order.
                .addAttribute(attr("multi")
                        .setStringValue("as-string")
                        .setDoubleValue(1.5)
                        .setIntValue(7L))
                // No direct value, but a nested 'value' sub-attribute: the
                // auto-detect chain falls through into it.
                .addAttribute(attr("measure")
                        .addProperty(attr("uom").setStringValue("m"))
                        .addProperty(attr("value").setDoubleValue(3.25)))
                // Nested children, but none named 'value' — no fallback exists.
                .addAttribute(attr("wrapper")
                        .addProperty(attr("other").setStringValue("ignored")));

        Map<String, Object> values = new AttributeEncoder().extractAttributes(feature);

        assertEquals(7L, values.get("multi"));
        assertEquals(3.25, values.get("measure"));
        assertFalse(values.containsKey("wrapper"),
                "fallback must only fire for a child literally named 'value'");
    }

    @Test
    void featureWithoutAttributesExtractsNothing() {
        assertTrue(new AttributeEncoder().extractAttributes(Feature.of(Name.of("Building"))).isEmpty());
    }

    // ---- FEATURE table -----------------------------------------------------

    @Test
    void featureSourceExtractsFirstClassFieldsAndDropsAbsentOnes() {
        Feature feature = Feature.of(Name.of("Building"))
                .setObjectId("DEBY_LOD2_4865511")
                .setIdentifier("urn:uuid:1234")
                .setCreationDate(OffsetDateTime.of(2019, 5, 6, 7, 8, 9, 0, ZoneOffset.UTC));

        Map<String, Object> values = extract(feature,
                "gmlid:FEATURE/objectid",
                "ident:FEATURE/identifier",
                "created:FEATURE/creationDate",
                "ended:FEATURE/terminationDate");

        assertEquals("DEBY_LOD2_4865511", values.get("gmlid"));
        assertEquals("urn:uuid:1234", values.get("ident"));
        // Temporal fields render to ISO-8601 so both writers see a string.
        assertEquals("2019-05-06T07:08:09Z", values.get("created"));
        assertFalse(values.containsKey("ended"), "an absent feature field must drop its column");
        // Mapping order is the user-typed declaration order.
        assertEquals(List.of("gmlid", "ident", "created"), List.copyOf(values.keySet()));
    }

    // ---- ATTRIBUTES: path walk --------------------------------------------

    @Test
    void dottedPathDescendsThroughChildAttributes() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("energy")
                        .addProperty(attr("demand")
                                .addProperty(attr("heating").setDoubleValue(88.5))));

        Map<String, Object> values = extract(feature,
                "heat:ATTRIBUTES/energy.demand.heating",
                // A path that leaves the tree partway must yield nothing rather
                // than falling back to the last surviving level.
                "gone:ATTRIBUTES/energy.demand.cooling",
                "deep:ATTRIBUTES/energy.demand.heating.deeper");

        assertEquals(88.5, values.get("heat"));
        assertFalse(values.containsKey("gone"));
        assertFalse(values.containsKey("deep"));
    }

    // ---- ATTRIBUTES: predicates -------------------------------------------

    @Test
    void predicateKeepsOnlySegmentsCarryingAMatchingChild() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("ref")
                        .addProperty(attr("scheme").setStringValue("ALKIS"))
                        .addProperty(attr("value").setStringValue("alkis-id")))
                .addAttribute(attr("ref")
                        .addProperty(attr("scheme").setStringValue("INSPIRE"))
                        .addProperty(attr("value").setStringValue("inspire-id")));

        Map<String, Object> values = extract(feature,
                "inspire:ATTRIBUTES/ref[scheme='INSPIRE']",
                "alkis:ATTRIBUTES/ref[scheme='ALKIS']",
                "none:ATTRIBUTES/ref[scheme='NOPE']");

        // The predicate scopes the segment's own children; the surviving
        // segment's cell value still comes from the 'value' fallback.
        assertEquals("inspire-id", values.get("inspire"));
        assertEquals("alkis-id", values.get("alkis"));
        assertFalse(values.containsKey("none"));
    }

    @Test
    void predicateScansAllSameNamedSiblingsNotJustTheFirst() {
        // Two 'role' children on one node; only the SECOND satisfies the
        // predicate. Stopping at the first sibling would silently drop the row.
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("part")
                        .addProperty(attr("role").setStringValue("secondary"))
                        .addProperty(attr("role").setStringValue("primary"))
                        .addProperty(attr("value").setStringValue("kept")));

        assertEquals("kept", extract(feature, "p:ATTRIBUTES/part[role='primary']").get("p"));
    }

    @Test
    void predicateComparesNumbersAcrossLongAndDouble() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("lod")
                        .addProperty(attr("level").setDoubleValue(2.0))
                        .addProperty(attr("value").setStringValue("lod2")))
                .addAttribute(attr("count")
                        .addProperty(attr("n").setIntValue(3L))
                        .addProperty(attr("value").setStringValue("three")));

        Map<String, Object> values = extract(feature,
                // Long literal against a Double actual ...
                "a:ATTRIBUTES/lod[level=2]",
                // ... and a Double literal against a Long actual.
                "b:ATTRIBUTES/count[n=3.0]",
                // Cross-type numeric equality must not degrade into "any number".
                "c:ATTRIBUTES/count[n=4]");

        assertEquals("lod2", values.get("a"));
        assertEquals("three", values.get("b"));
        assertFalse(values.containsKey("c"));
    }

    @Test
    void predicatesComposeIndependentlyAtEachPathSegment() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("survey")
                        .addProperty(attr("year").setIntValue(2020L))
                        .addProperty(attr("reading")
                                .addProperty(attr("unit").setStringValue("kWh"))
                                .addProperty(attr("value").setDoubleValue(11.0)))
                        .addProperty(attr("reading")
                                .addProperty(attr("unit").setStringValue("MWh"))
                                .addProperty(attr("value").setDoubleValue(0.011))))
                .addAttribute(attr("survey")
                        .addProperty(attr("year").setIntValue(2021L))
                        .addProperty(attr("reading")
                                .addProperty(attr("unit").setStringValue("kWh"))
                                .addProperty(attr("value").setDoubleValue(9.5))));

        Map<String, Object> values = extract(feature,
                "kwh2021:ATTRIBUTES/survey[year=2021].reading[unit='kWh']",
                "mwh2020:ATTRIBUTES/survey[year=2020].reading[unit='MWh']",
                // Both predicates individually match some node, but no single
                // path satisfies them together.
                "mwh2021:ATTRIBUTES/survey[year=2021].reading[unit='MWh']");

        assertEquals(9.5, values.get("kwh2021"));
        assertEquals(0.011, values.get("mwh2020"));
        assertFalse(values.containsKey("mwh2021"));
    }

    // ---- ATTRIBUTES: aggregates -------------------------------------------

    @Test
    void aggregatesReduceSurvivingRowsToOneCell() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("usage").setStringValue("living"))
                .addAttribute(attr("usage").setStringValue("office"))
                .addAttribute(attr("usage").setStringValue("retail"));

        Map<String, Object> values = extract(feature,
                "first:ATTRIBUTES/usage",
                "explicitFirst:ATTRIBUTES/[FIRST]usage",
                "last:ATTRIBUTES/[LAST]usage",
                "count:ATTRIBUTES/[COUNT]usage",
                "all:ATTRIBUTES/[ALL]usage");

        assertEquals("living", values.get("first"), "no bracket must default to FIRST");
        assertEquals("living", values.get("explicitFirst"));
        assertEquals("retail", values.get("last"));
        assertEquals(3L, values.get("count"));
        assertEquals("living; office; retail", values.get("all"));
    }

    @Test
    void countAlwaysYieldsACellWhileOtherAggregatesDropTheColumn() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("usage").setStringValue("living"));

        Map<String, Object> values = extract(feature,
                "missingCount:ATTRIBUTES/[COUNT]absent",
                "missingAll:ATTRIBUTES/[ALL]absent",
                "missingFirst:ATTRIBUTES/absent",
                "missingLast:ATTRIBUTES/[LAST]absent");

        // "how many X?" must answer 0 rather than vanishing from the table.
        assertEquals(0L, values.get("missingCount"));
        assertFalse(values.containsKey("missingAll"));
        assertFalse(values.containsKey("missingFirst"));
        assertFalse(values.containsKey("missingLast"));

        // Same on a feature carrying no attribute tree at all.
        Map<String, Object> bare = extract(Feature.of(Name.of("Building")),
                "c:ATTRIBUTES/[COUNT]usage", "f:ATTRIBUTES/usage");
        assertEquals(0L, bare.get("c"));
        assertFalse(bare.containsKey("f"));
    }

    @Test
    void valuelessRowsAreDroppedBeforeAggregation() {
        // The middle occurrence carries no extractable value: it must not
        // appear as "null" in the joined string nor inflate the count.
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("usage").setStringValue("living"))
                .addAttribute(attr("usage"))
                .addAttribute(attr("usage").setStringValue("office"));

        Map<String, Object> values = extract(feature,
                "all:ATTRIBUTES/[ALL]usage",
                "count:ATTRIBUTES/[COUNT]usage",
                "last:ATTRIBUTES/[LAST]usage");

        assertEquals("living; office", values.get("all"));
        assertEquals(2L, values.get("count"));
        assertEquals("office", values.get("last"));
    }

    // ---- ATTRIBUTES: ::type cast ------------------------------------------

    @Test
    void valueTypeCastPullsExactlyTheNamedSlot() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("roofType")
                        .setStringValue("1000")
                        .setCodeSpace("https://example.org/codelists/roof"))
                .addAttribute(attr("height")
                        .setDoubleValue(12.5)
                        .setUom("m"))
                .addAttribute(attr("blob")
                        .setGenericContent("<xml/>")
                        .setGenericContentMimeType("text/xml"))
                .addAttribute(attr("tags").setArrayValue(ArrayValue.ofLong(List.of(1L, 2L))))
                .addAttribute(attr("when").setTimeStamp(
                        OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC)));

        Map<String, Object> values = extract(feature,
                "code:ATTRIBUTES/roofType::string",
                "codeSpace:ATTRIBUTES/roofType::code",
                "h:ATTRIBUTES/height::double",
                "unit:ATTRIBUTES/height::uom",
                "content:ATTRIBUTES/blob::content",
                "mime:ATTRIBUTES/blob::mimeType",
                "tags:ATTRIBUTES/tags::array",
                "ts:ATTRIBUTES/when::timestamp",
                // The named slot is empty on this leaf — a cast never falls
                // back to another slot, so the column drops.
                "wrongSlot:ATTRIBUTES/roofType::int");

        assertEquals("1000", values.get("code"));
        assertEquals("https://example.org/codelists/roof", values.get("codeSpace"));
        assertEquals(12.5, values.get("h"));
        assertEquals("m", values.get("unit"));
        assertEquals("<xml/>", values.get("content"));
        assertEquals("text/xml", values.get("mime"));
        assertEquals("1; 2", values.get("tags"));
        assertEquals("2020-01-02T03:04:05Z", values.get("ts"),
                "::timestamp must render the same ISO string as auto-detection");
        assertFalse(values.containsKey("wrongSlot"));
    }

    @Test
    void valueTypeCastDisablesTheValueSubAttributeFallback() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAttribute(attr("measure")
                        .addProperty(attr("value").setDoubleValue(3.25)));

        Map<String, Object> values = extract(feature,
                "auto:ATTRIBUTES/measure",
                "cast:ATTRIBUTES/measure::double",
                // Reaching the nested value explicitly is the supported route.
                "explicit:ATTRIBUTES/measure.value::double");

        assertEquals(3.25, values.get("auto"));
        assertFalse(values.containsKey("cast"),
                "a cast must read the leaf's own slot only — no recursion into 'value'");
        assertEquals(3.25, values.get("explicit"));
    }

    // ---- ADDRESS -----------------------------------------------------------

    @Test
    void addressFieldsAreExtractedAndFilteredByPredicate() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAddress(address(Address.newInstance()
                        .setStreet("Schellingstr.")
                        .setHouseNumber("24")
                        .setZipCode("70174")
                        .setCity("Stuttgart")))
                .addAddress(address(Address.newInstance()
                        .setStreet("Marienplatz")
                        .setHouseNumber("1")
                        .setCity("Munich")));

        Map<String, Object> values = extract(feature,
                "street:ADDRESS/street",
                "lastCity:ADDRESS/[LAST]city",
                "allStreets:ADDRESS/[ALL]street",
                "muc:ADDRESS/street[city='Munich']",
                "zip:ADDRESS/zipCode[city='Munich']",
                "nowhere:ADDRESS/street[city='Berlin']",
                "n:ADDRESS/[COUNT]street");

        assertEquals("Schellingstr.", values.get("street"));
        assertEquals("Munich", values.get("lastCity"));
        assertEquals("Schellingstr.; Marienplatz", values.get("allStreets"));
        assertEquals("Marienplatz", values.get("muc"), "the predicate filters address ROWS");
        // The Munich row has no zip code: the row survives the predicate but
        // contributes no value, so the column drops.
        assertFalse(values.containsKey("zip"));
        assertFalse(values.containsKey("nowhere"));
        assertEquals(2L, values.get("n"));
    }

    @Test
    void addressCountIgnoresRowsWithoutTheProjectedField() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAddress(address(Address.newInstance().setStreet("A-Street").setCity("X")))
                .addAddress(address(Address.newInstance().setCity("X")));

        Map<String, Object> values = extract(feature,
                "streets:ADDRESS/[COUNT]street",
                "cities:ADDRESS/[COUNT]city");

        assertEquals(1L, values.get("streets"),
                "COUNT counts present values, not address rows");
        assertEquals(2L, values.get("cities"));
    }

    @Test
    void addressFreeTextArrayIsJoinedRatherThanToStringed() {
        Feature feature = Feature.of(Name.of("Building"))
                .addAddress(address(Address.newInstance()
                        .setFreeText(ArrayValue.ofString(List.of("c/o Meier", "Rear building")))));

        String text = (String) extract(feature, "ft:ADDRESS/freeText").get("ft");

        assertEquals("c/o Meier; Rear building", text);
        assertFalse(text.contains("@"), "ArrayValue must never leak ClassName@hashcode");
    }

    @Test
    void featureWithoutAddressesYieldsNoAddressColumns() {
        Map<String, Object> values = extract(Feature.of(Name.of("Building")),
                "s:ADDRESS/street", "n:ADDRESS/[COUNT]street");

        assertFalse(values.containsKey("s"));
        assertEquals(0L, values.get("n"));
    }

    // ---- static array formatting ------------------------------------------

    @Test
    void formatArrayValueReturnsNullForNullAndEmptyArrays() {
        assertNull(AttributeEncoder.formatArrayValue(null));
        assertNull(AttributeEncoder.formatArrayValue(ArrayValue.newInstance()),
                "an empty array must read as no value so aggregators drop the row");
        assertEquals("1; 2.5; true; x",
                AttributeEncoder.formatArrayValue(ArrayValue.ofList(List.of(1L, 2.5, true, "x"))));
    }

    // ---- schema finalization ----------------------------------------------

    @Test
    void finalizeFieldsLeadsWithTheFixedIdentityTriple() {
        List<AttrField> fields = new AttributeEncoder().finalizeFields(0);

        assertEquals(List.of(
                new AttrField("featureType", AttrType.STRING),
                new AttrField("OID", AttrType.OID),
                new AttrField("OBJECTID", AttrType.STRING)), fields);
    }

    @Test
    void withoutProjectionUserFieldsAreAlphabeticalForReproducibleOutput() {
        AttributeEncoder encoder = new AttributeEncoder();
        encoder.trackFieldTypes(Map.of("zulu", "z", "alpha", "a", "mike", "m"));

        assertEquals(List.of("featureType", "OID", "OBJECTID", "alpha", "mike", "zulu"),
                names(encoder.finalizeFields(1)));
    }

    @Test
    void projectionPreservesDeclarationOrderAndDropsNeverPopulatedColumns() {
        AttributeEncoder encoder = new AttributeEncoder();
        encoder.setProjection(AttributeProjection.parse(List.of(
                "zulu:ATTRIBUTES/z",
                "typo:ATTRIBUTES/nonexistent",
                "alpha:ATTRIBUTES/a")));
        encoder.trackFieldTypes(Map.of("zulu", "z-value", "alpha", 1L));

        // Declaration order wins over alphabetical; 'typo' never produced a
        // value on any feature and is left out of the schema entirely.
        assertEquals(List.of("featureType", "OID", "OBJECTID", "zulu", "alpha"),
                names(encoder.finalizeFields(1)));
    }

    @Test
    void mixedValueClassesPromoteToTheWidestType() {
        AttributeEncoder encoder = new AttributeEncoder();
        encoder.trackFieldTypes(Map.of("mixedNum", 1L, "mixedStr", 2L, "pureInt", 3L));
        encoder.trackFieldTypes(Map.of("mixedNum", 1.5, "mixedStr", "text", "pureInt", 4L));

        Map<String, AttrType> types = types(encoder.finalizeFields(2));
        assertEquals(AttrType.DOUBLE, types.get("mixedNum"), "INT + DOUBLE -> DOUBLE");
        assertEquals(AttrType.STRING, types.get("mixedStr"), "any STRING involvement -> STRING");
        assertEquals(AttrType.INT, types.get("pureInt"));
    }

    @Test
    void longsOutsideInt32RangePromoteToDoubleRatherThanTruncate() {
        AttributeEncoder encoder = new AttributeEncoder();
        encoder.trackFieldTypes(Map.of(
                "maxInt", (long) Integer.MAX_VALUE,
                "minInt", (long) Integer.MIN_VALUE,
                "tooBig", Integer.MAX_VALUE + 1L,
                "tooSmall", Integer.MIN_VALUE - 1L));

        Map<String, AttrType> types = types(encoder.finalizeFields(1));
        assertEquals(AttrType.INT, types.get("maxInt"), "Int32 bounds are inclusive");
        assertEquals(AttrType.INT, types.get("minInt"));
        assertEquals(AttrType.DOUBLE, types.get("tooBig"));
        assertEquals(AttrType.DOUBLE, types.get("tooSmall"));
    }

    @Test
    void sparseIntFieldsPromoteToDoubleSoMissingValuesCanBeNaN() {
        AttributeEncoder encoder = new AttributeEncoder();
        // 'storeys' is present on 1 of 3 features; 'always' on all 3.
        encoder.trackFieldTypes(Map.of("storeys", 4L, "always", 1L));
        encoder.trackFieldTypes(Map.of("always", 2L));
        encoder.trackFieldTypes(Map.of("always", 3L));

        Map<String, AttrType> types = types(encoder.finalizeFields(3));
        assertEquals(AttrType.DOUBLE, types.get("storeys"),
                "a gap-carrying INT column must widen to DOUBLE so NaN can mark the gaps");
        assertEquals(AttrType.INT, types.get("always"));
    }

    @Test
    void gapPromotionAppliesOnlyToIntColumns() {
        AttributeEncoder encoder = new AttributeEncoder();
        // Both columns are present on 1 of 3 features, but only INT carries
        // the NaN-for-missing concern — STRING and DOUBLE stay as they are.
        encoder.trackFieldTypes(Map.of("text", "x", "real", 1.5));
        encoder.trackFieldTypes(Map.of());
        encoder.trackFieldTypes(Map.of());

        Map<String, AttrType> types = types(encoder.finalizeFields(3));
        assertEquals(AttrType.STRING, types.get("text"));
        assertEquals(AttrType.DOUBLE, types.get("real"));
    }

    // ---- fixtures ----------------------------------------------------------

    private static Attribute attr(String localName) {
        return Attribute.of(Name.of(localName));
    }

    private static AddressProperty address(Address address) {
        return AddressProperty.of(Name.of("address"), address);
    }

    /** Install the mappings and evaluate them against {@code feature}. */
    private static Map<String, Object> extract(Feature feature, String... mappings) {
        AttributeEncoder encoder = new AttributeEncoder();
        encoder.setProjection(AttributeProjection.parse(List.of(mappings)));
        return encoder.extractAttributes(feature);
    }

    private static List<String> names(List<AttrField> fields) {
        return fields.stream().map(AttrField::name).toList();
    }

    private static Map<String, AttrType> types(List<AttrField> fields) {
        return fields.stream().collect(
                java.util.stream.Collectors.toMap(AttrField::name, AttrField::type));
    }
}
