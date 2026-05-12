/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.attribute;

import org.citydb.model.address.Address;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.AddressProperty;
import org.citydb.model.property.ArrayValue;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.Property;
import org.citydb.model.property.Value;
import org.citydb.vis.model.AttrField;
import org.citydb.vis.model.AttrType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Format-agnostic attribute type detection and extraction.
 * <p>
 * Call {@link #trackFieldTypes} during the write phase, then
 * {@link #finalizeFields} during close. Thread-safe — uses concurrent
 * data structures.
 * <p>
 * Subclasses add format-specific binary encoding (e.g., I3S binary
 * attribute buffers, 3D Tiles batch table / EXT_structural_metadata).
 * <p>
 * An optional {@link AttributeProjection} narrows extraction to a
 * declarative mapping list. Without a projection every top-level
 * attribute is exported (default behaviour).
 */
public class AttributeEncoder {
    private static final Logger logger = LoggerFactory.getLogger(AttributeEncoder.class);
    private static final String ALL_JOIN_SEPARATOR = "; ";

    private final ConcurrentHashMap<String, AttrType> trackedTypes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> trackedCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AttributeStats> stats = new ConcurrentHashMap<>();

    // volatile so the projection set during single-threaded writer
    // construction is safely visible to worker threads that later read
    // it in extractAttributes / evaluate. Cheap insurance against a
    // future refactor that starts background work before VisWriter's
    // ctor returns.
    private volatile AttributeProjection projection;

    /**
     * Install the projection. May be called at most once, before any
     * feature is written. {@code null} disables the projection (full
     * extraction).
     */
    public void setProjection(AttributeProjection projection) {
        this.projection = projection;
    }

    /**
     * Track attribute field types incrementally during the write phase.
     * Thread-safe — can be called concurrently from multiple writer threads.
     */
    public void trackFieldTypes(Map<String, Object> attributes) {
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String name = entry.getKey();
            AttrType valueType = classifyType(entry.getValue());
            trackedTypes.merge(name, valueType, AttributeEncoder::promoteType);
            trackedCounts.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * Finalize attribute fields from incrementally tracked types.
     * Must be called after all features have been processed.
     * <p>
     * Order: {@code featureType} first (most informative for popups),
     * then the two fixed identifier fields ({@code OID}, {@code OBJECTID}),
     * then user attributes. With a projection installed, user attributes
     * follow the projection's declared mapping order so the schema mirrors
     * the user-typed column sequence. Without a projection, alphabetical
     * (ConcurrentHashMap iteration order is unspecified — we want
     * reproducible output across runs).
     *
     * @param totalFeatures total number of features processed
     * @return ordered list of attribute fields with final types
     */
    public List<AttrField> finalizeFields(long totalFeatures) {
        Map<String, AttrType> result = new LinkedHashMap<>();
        // featureType: what kind of city object was picked. First so that
        // popup-style viewers (Cesium info-box, ArcGIS pop-up) lead with
        // the most informative field.
        result.put("featureType", AttrType.STRING);
        // OID: Esri integer OID (esriFieldTypeOID / Oid32). Value =
        // sequential feature counter from VisWriter.featureIdCounter.
        // Used by ArcGIS Pro for single-feature picking.
        result.put("OID", AttrType.OID);
        // OBJECTID: the CityGML gml:id string (e.g., "DEBY_LOD2_4865511").
        // Matches the identifier users see in Cesium's picking popup, so
        // ArcGIS and Cesium both surface the same human-readable value.
        result.put("OBJECTID", AttrType.STRING);

        List<String> userOrder;
        if (projection != null) {
            userOrder = projection.declaredOutputColumns();
        } else {
            userOrder = new ArrayList<>(new TreeMap<>(trackedTypes).keySet());
        }
        for (String name : userOrder) {
            AttrType type = trackedTypes.get(name);
            if (type == null) {
                // Declared in projection but no feature populated it.
                // Could be a typo OR a legitimately empty field in the
                // source data — the WARN is neutral so the user
                // investigates which it is.
                logger.warn("Mapping output column '{}' produced no value on any of {} features. " +
                        "Either the source expression has a typo or the field is empty " +
                        "throughout the dataset.", name, totalFeatures);
                continue;
            }
            if (type == AttrType.INT) {
                long count = trackedCounts.getOrDefault(name, new AtomicLong(0)).get();
                if (count < totalFeatures) {
                    type = AttrType.DOUBLE; // promote: NaN represents missing values
                }
            }
            result.put(name, type);
        }

        List<AttrField> fields = new ArrayList<>();
        result.forEach((name, type) -> fields.add(new AttrField(name, type)));
        return fields;
    }

    /**
     * Update per-attribute statistics. Called by writer subclasses for
     * each (field, value) pair while iterating per-node features —
     * thread-safe via {@link AttributeStats}'s synchronized internals plus
     * the {@code computeIfAbsent} guard. The accumulator chosen
     * (numeric vs string) is fixed by the field's resolved
     * {@link AttrType} after type tracking finalizes.
     */
    public void updateStats(AttrField field, Object value) {
        AttributeStats accumulator = stats.computeIfAbsent(field.name(), name ->
                field.type() == AttrType.STRING ? AttributeStats.forString() : AttributeStats.forNumeric());
        accumulator.update(value);
    }

    /**
     * Snapshot of all per-attribute statistics gathered during the write
     * phase. Returned in a fresh map keyed by attribute name; the entries
     * themselves are immutable {@link AttributeStats.Result} records.
     */
    public Map<String, AttributeStats.Result> snapshotStats() {
        Map<String, AttributeStats.Result> result = new LinkedHashMap<>();
        stats.forEach((name, s) -> result.put(name, s.toResult()));
        return result;
    }

    // ---- Attribute extraction ----

    /**
     * Extract typed values from a feature. With a projection installed,
     * iterate its mappings and pull one value per mapping. Without a
     * projection, emit every top-level attribute (default behaviour).
     */
    public Map<String, Object> extractAttributes(Feature feature) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (projection != null) {
            for (AttributeProjection.Mapping mapping : projection.mappings()) {
                Object value = evaluate(feature, mapping.source());
                if (value != null) {
                    result.put(mapping.outputColumn(), value);
                }
            }
            return result;
        }
        if (feature.hasAttributes()) {
            for (Attribute attr : feature.getAttributes().getAll()) {
                String name = attr.getName().getLocalName();
                Object value = getAttributeValue(attr);
                if (value != null) {
                    result.put(name, value);
                }
            }
        }
        return result;
    }

    private Object evaluate(Feature feature, AttributeProjection.Source source) {
        // Source is a sealed interface — the three branches below exhaust
        // every variant. Adding a fourth requires a new branch here; the
        // AssertionError makes the omission impossible to miss in testing.
        if (source instanceof AttributeProjection.FeatureSource f) {
            return f.field().extract(feature);
        }
        if (source instanceof AttributeProjection.AttributesSource a) {
            return evaluateAttributes(feature, a);
        }
        if (source instanceof AttributeProjection.AddressSource a) {
            return evaluateAddress(feature, a);
        }
        throw new AssertionError("Unhandled Source variant: " + source.getClass());
    }

    // ---- ATTRIBUTES ----

    /**
     * Walk the path one segment at a time. At each step:
     *   1. Collect candidates by localName under the previous level
     *      (top-level attributes for segment 0; children of the
     *      previous step's matches for segment 1+).
     *   2. If the segment carries a predicate, drop any candidate whose
     *      own children don't include a matching {@code k=v}.
     * The surviving leaves are then aggregated to a single cell value.
     * <p>
     * Leaf value extraction follows {@link AttributesSource#valueType}:
     * when present, strict typed extraction (no auto-fallback into a
     * {@code "value"} sub-attribute); when null, the conventional
     * auto-detection in {@link #getAttributeValue}.
     */
    private Object evaluateAttributes(Feature feature, AttributeProjection.AttributesSource source) {
        java.util.function.Function<Attribute, Object> leafExtractor = source.valueType() != null
                ? source.valueType()::extract
                : AttributeEncoder::getAttributeValue;

        List<AttributeProjection.PathSegment> path = source.path();
        if (!feature.hasAttributes()) {
            return aggregate(List.of(), source.aggregate(), leafExtractor);
        }
        List<Attribute> current = new ArrayList<>();
        AttributeProjection.PathSegment first = path.get(0);
        for (Attribute attr : feature.getAttributes().getAll()) {
            if (first.localName().equals(attr.getName().getLocalName())) {
                current.add(attr);
            }
        }
        current = applyPredicate(current, first.predicate());

        for (int i = 1; i < path.size() && !current.isEmpty(); i++) {
            AttributeProjection.PathSegment segment = path.get(i);
            List<Attribute> next = new ArrayList<>();
            for (Attribute parent : current) {
                if (!parent.hasProperties()) {
                    continue;
                }
                for (Property<?> prop : parent.getProperties().getAll()) {
                    if (prop instanceof Attribute sub
                            && segment.localName().equals(sub.getName().getLocalName())) {
                        next.add(sub);
                    }
                }
            }
            current = applyPredicate(next, segment.predicate());
        }
        return aggregate(current, source.aggregate(), leafExtractor);
    }

    private static List<Attribute> applyPredicate(List<Attribute> candidates,
                                                  AttributeProjection.Predicate predicate) {
        if (predicate == null || candidates.isEmpty()) {
            return candidates;
        }
        List<Attribute> out = new ArrayList<>(candidates.size());
        for (Attribute candidate : candidates) {
            if (hasMatchingChild(candidate, predicate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    /**
     * True iff {@code parent} carries at least one direct {@link Attribute}
     * child whose localName matches {@code predicate.field()} and whose
     * value compares equal to {@code predicate.value()}. Iterates ALL
     * same-named children (not just the first) so multi-occurrence siblings
     * cannot silently drop the row when the first sibling's value happens
     * not to satisfy the predicate.
     */
    private static boolean hasMatchingChild(Attribute parent, AttributeProjection.Predicate predicate) {
        if (!parent.hasProperties()) {
            return false;
        }
        String field = predicate.field();
        Object expected = predicate.value();
        for (Property<?> prop : parent.getProperties().getAll()) {
            if (prop instanceof Attribute sub
                    && field.equals(sub.getName().getLocalName())
                    && valuesEqual(getAttributeValue(sub), expected)) {
                return true;
            }
        }
        return false;
    }

    // ---- ADDRESS ----

    private Object evaluateAddress(Feature feature, AttributeProjection.AddressSource source) {
        List<Address> addresses = new ArrayList<>();
        if (feature.hasAddresses()) {
            for (AddressProperty property : feature.getAddresses().getAll()) {
                property.getObject().ifPresent(addresses::add);
            }
        }
        if (source.predicate() != null) {
            // Parser already validated that predicate.field() resolves
            // to a known AddressField — forName here cannot return null.
            AddressField predicateField = AddressField.forName(source.predicate().field());
            List<Address> filtered = new ArrayList<>(addresses.size());
            for (Address address : addresses) {
                if (valuesEqual(predicateField.extract(address), source.predicate().value())) {
                    filtered.add(address);
                }
            }
            addresses = filtered;
        }
        AddressField field = source.field();
        return aggregate(addresses, source.aggregate(), field::extract);
    }

    // ---- aggregate ----

    /**
     * Reduce a list of source rows into a single cell value. The mapper
     * pulls the per-row scalar; rows whose scalar is {@code null} are
     * dropped before aggregation so {@code ALL}/{@code FIRST}/{@code LAST}
     * never produce {@code null} mid-string and {@code COUNT} reports
     * only present values.
     */
    private static <T> Object aggregate(List<T> rows,
                                        AttributeProjection.Aggregate aggregate,
                                        java.util.function.Function<T, Object> mapper) {
        List<Object> values = new ArrayList<>(rows.size());
        for (T row : rows) {
            Object value = mapper.apply(row);
            if (value != null) {
                values.add(value);
            }
        }
        return switch (aggregate) {
            case FIRST -> values.isEmpty() ? null : values.get(0);
            case LAST -> values.isEmpty() ? null : values.get(values.size() - 1);
            // COUNT always produces a value (0 included) — required so
            // "how many X did this feature have?" yields a real number
            // rather than dropping the column entirely.
            case COUNT -> (long) values.size();
            case ALL -> {
                if (values.isEmpty()) yield null;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) sb.append(ALL_JOIN_SEPARATOR);
                    sb.append(values.get(i));
                }
                yield sb.toString();
            }
        };
    }

    // ---- value equality (predicate) ----

    /**
     * Equality between an extracted attribute / address value and a
     * predicate literal. Numerics ({@code Long}, {@code Double}) compare
     * across types via {@code doubleValue()}; other classes require
     * exact-class {@code equals}. {@code null} actuals never match.
     */
    private static boolean valuesEqual(Object actual, Object expected) {
        if (actual == null) {
            return false;
        }
        if (actual instanceof Number an && expected instanceof Number en) {
            return an.doubleValue() == en.doubleValue();
        }
        return actual.equals(expected);
    }

    // ---- Internal helpers ----

    private static AttrType classifyType(Object value) {
        if (value instanceof Long l) {
            // INT maps to Int32. Long values outside that range would be
            // silently truncated, so promote to DOUBLE (Float64 covers all
            // safe-integer longs up to 2^53 exactly).
            return (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE)
                    ? AttrType.INT : AttrType.DOUBLE;
        }
        if (value instanceof Double) return AttrType.DOUBLE;
        return AttrType.STRING;
    }

    private static Object getAttributeValue(Attribute attr) {
        if (attr.getIntValue().isPresent()) {
            return attr.getIntValue().get();
        }
        if (attr.getDoubleValue().isPresent()) {
            return attr.getDoubleValue().get();
        }
        if (attr.getStringValue().isPresent()) {
            return attr.getStringValue().get();
        }
        if (attr.getTimeStamp().isPresent()) {
            return attr.getTimeStamp().get().toString();
        }
        if (attr.getURI().isPresent()) {
            return attr.getURI().get();
        }
        if (attr.getArrayValue().isPresent()) {
            return formatArrayValue(attr.getArrayValue().get());
        }
        if (attr.hasProperties()) {
            for (Property<?> prop : attr.getProperties().getAll()) {
                if (prop instanceof Attribute sub && "value".equals(sub.getName().getLocalName())) {
                    Object val = getAttributeValue(sub);
                    if (val != null) {
                        return val;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Render an {@link ArrayValue} as a {@code "; "}-joined string of
     * its raw element values. {@code ArrayValue} does not override
     * {@code toString()}, so a naive {@code Object::toString} would
     * produce {@code ClassName@hashcode} — this helper is the canonical
     * stringification used by both the auto-detect chain
     * ({@link #getAttributeValue}) and the {@code FREE_TEXT} address
     * field. Returns {@code null} for an empty array so aggregators
     * treat it as no value.
     */
    static String formatArrayValue(ArrayValue array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Value v : array.getValues()) {
            if (!first) sb.append(ALL_JOIN_SEPARATOR);
            sb.append(v.rawValue());
            first = false;
        }
        return sb.toString();
    }

    private static AttrType promoteType(AttrType a, AttrType b) {
        if (a == b) return a;
        if (a == AttrType.STRING || b == AttrType.STRING) return AttrType.STRING;
        return AttrType.DOUBLE;
    }
}
