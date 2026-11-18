/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.encoding;

import org.citydb.query.filter.Filter;
import org.citydb.query.filter.common.Expression;
import org.citydb.query.filter.literal.PropertyRef;
import org.citydb.query.filter.operation.ArithmeticExpression;
import org.citydb.query.filter.operation.BinaryComparisonPredicate;
import org.citydb.query.filter.operation.BinarySpatialPredicate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PropertyResolverTest {
    private static final Map<String, String> ALIASES = Map.of(
            "totalStoreys", "storeysAboveGround + storeysBelowGround",
            "city", "address.city",
            "name", "core:name");

    private final List<PropertyRef> resolved = new ArrayList<>();
    private final List<String> steps = new ArrayList<>();
    private final PropertyResolver resolver = (propertyRef, step) -> {
        resolved.add(propertyRef);
        steps.add(step != null ? name(step.first()) : null);

        // only properties of the filtered feature are aliases
        String alias = step == null ? ALIASES.get(name(propertyRef)) : null;
        return alias != null
                ? Optional.of(FilterTextParser.newInstance().parse(alias))
                : Optional.empty();
    };

    @Test
    void resolvedPropertiesAreReplacedInText() throws FilterParseException {
        BinaryComparisonPredicate predicate = (BinaryComparisonPredicate) Filter.ofText("totalStoreys > 3", resolver)
                .getExpression();
        assertInstanceOf(ArithmeticExpression.class, predicate.getLeftOperand());
        assertEquals(List.of("totalStoreys"), names());
    }

    @Test
    void resolvedPropertiesAreReplacedInJson() throws FilterParseException {
        BinaryComparisonPredicate predicate = (BinaryComparisonPredicate) Filter.ofJSON(
                "{\"op\": \">\", \"args\": [{\"property\": \"totalStoreys\"}, 3]}", resolver).getExpression();
        assertInstanceOf(ArithmeticExpression.class, predicate.getLeftOperand());
        assertEquals(List.of("totalStoreys"), names());
    }

    @Test
    void unresolvedPropertiesStayPropertyReferences() throws FilterParseException {
        BinaryComparisonPredicate predicate = (BinaryComparisonPredicate) Filter.ofText("bldg:height > 10", resolver)
                .getExpression();
        PropertyRef propertyRef = assertInstanceOf(PropertyRef.class, predicate.getLeftOperand());
        assertEquals("height", propertyRef.getName().getLocalName());
        assertEquals(List.of("bldg:height"), names());
    }

    @Test
    void resolverReceivesTheWholePath() throws FilterParseException {
        Filter.ofText("address.city = 'Helsinki'", resolver);
        Filter.ofJSON("{\"op\": \"=\", \"args\": [{\"property\": \"address.city\"}, \"Helsinki\"]}", resolver);
        assertEquals(List.of("address.city", "address.city"), names());
    }

    @Test
    void propertiesOnBothSidesAndInFunctionsAreResolved() throws FilterParseException {
        BinaryComparisonPredicate predicate = (BinaryComparisonPredicate) Filter.ofText(
                "CASEI(city) = totalStoreys", resolver).getExpression();
        assertInstanceOf(ArithmeticExpression.class, predicate.getRightOperand());
        assertTrue(names().containsAll(List.of("city", "totalStoreys")));
    }

    @Test
    void propertiesInStepPredicatesArePassedWithTheirStep() throws FilterParseException {
        for (Filter filter : List.of(
                Filter.ofText("boundary[name = 'x'].name = 'y'", resolver),
                Filter.ofJSON("{\"op\": \"=\", \"args\": [{\"property\": \"boundary[name = 'x'].name\"}, \"y\"]}",
                        resolver))) {
            // the name in the step predicate stays relative to the step, although "name" is an alias
            BinaryComparisonPredicate predicate = (BinaryComparisonPredicate) filter.getExpression();
            PropertyRef boundary = assertInstanceOf(PropertyRef.class, predicate.getLeftOperand());
            BinaryComparisonPredicate stepPredicate = assertInstanceOf(BinaryComparisonPredicate.class,
                    boundary.getFilter().orElseThrow());
            PropertyRef name = assertInstanceOf(PropertyRef.class, stepPredicate.getLeftOperand());
            assertTrue(name.getName().getPrefix().isEmpty());
        }

        // every property is passed: first the one in the step predicate, then the top-level one
        assertEquals(List.of("name", "boundary.name", "name", "boundary.name"), names());
        assertEquals(Arrays.asList("boundary", null, "boundary", null), steps);
    }

    @Test
    void signIsAppliedToResolvedExpression() throws FilterParseException {
        BinaryComparisonPredicate predicate = (BinaryComparisonPredicate) Filter.ofText("-totalStoreys < 0", resolver)
                .getExpression();
        assertInstanceOf(ArithmeticExpression.class, predicate.getLeftOperand());
    }

    @Test
    void resolvedExpressionMustFitTheContext() {
        assertThrows(FilterParseException.class,
                () -> Filter.ofText("S_INTERSECTS(totalStoreys, POINT(1 2))", resolver));
    }

    @Test
    void resolverMayRejectProperties() {
        PropertyResolver strict = (propertyRef, step) -> {
            throw new FilterParseException("The property '" + name(propertyRef) + "' is not a queryable.");
        };

        FilterParseException e = assertThrows(FilterParseException.class, () -> Filter.ofText("foo = 1", strict));
        assertEquals("The property 'foo' is not a queryable.", e.getMessage());
        assertThrows(FilterParseException.class, () -> Filter.ofText("boundary[name = 'x'].name = 'y'", strict));
    }

    @Test
    void withoutResolverNothingChanges() throws FilterParseException {
        Expression expression = Filter.ofText("S_INTERSECTS(envelope, POINT(1 2))").getExpression();
        assertInstanceOf(BinarySpatialPredicate.class, expression);
    }

    private List<String> names() {
        return resolved.stream().map(PropertyResolverTest::name).toList();
    }

    private static String name(PropertyRef propertyRef) {
        List<String> steps = new ArrayList<>();
        for (PropertyRef step = propertyRef; step != null; step = step.getChild().orElse(null)) {
            String localName = step.getName().getLocalName();
            steps.add(step.getName().getPrefix()
                    .map(prefix -> prefix + ":" + localName)
                    .orElse(localName));
        }

        return steps.stream().collect(Collectors.joining("."));
    }
}
