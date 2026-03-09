/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.operation;

import org.citydb.query.filter.encoding.JSONToken;
import org.citydb.query.filter.encoding.TextToken;

import java.util.EnumSet;

public enum SpatialOperator {
    CONTAINS(JSONToken.S_CONTAINS, TextToken.S_CONTAINS),
    CROSSES(JSONToken.S_CROSSES, TextToken.S_CROSSES),
    DISJOINT(JSONToken.S_DISJOINT, TextToken.S_DISJOINT),
    EQUALS(JSONToken.S_EQUALS, TextToken.S_EQUALS),
    INTERSECTS(JSONToken.S_INTERSECTS, TextToken.S_INTERSECTS),
    OVERLAPS(JSONToken.S_OVERLAPS, TextToken.S_OVERLAPS),
    TOUCHES(JSONToken.S_TOUCHES, TextToken.S_TOUCHES),
    WITHIN(JSONToken.S_WITHIN, TextToken.S_WITHIN),
    DWITHIN(JSONToken.S_DWITHIN, TextToken.S_DWITHIN),
    BEYOND(JSONToken.S_BEYOND, TextToken.S_BEYOND);

    public static final EnumSet<SpatialOperator> BINARY_OPERATORS = EnumSet.of(CONTAINS, CROSSES, DISJOINT, EQUALS,
            INTERSECTS, OVERLAPS, TOUCHES, WITHIN);

    private final JSONToken jsonToken;
    private final TextToken textToken;

    SpatialOperator(JSONToken jsonToken, TextToken textToken) {
        this.jsonToken = jsonToken;
        this.textToken = textToken;
    }

    public static SpatialOperator of(JSONToken token) {
        for (SpatialOperator operator : values()) {
            if (operator.jsonToken == token) {
                return operator;
            }
        }

        return null;
    }

    public static SpatialOperator of(TextToken token) {
        for (SpatialOperator operator : values()) {
            if (operator.textToken == token) {
                return operator;
            }
        }

        return null;
    }

    public JSONToken getJSONToken() {
        return jsonToken;
    }

    public TextToken getTextToken() {
        return textToken;
    }

    @Override
    public String toString() {
        return jsonToken.toString();
    }
}
