/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.function;

import java.util.*;

public enum FunctionName {
    ACCENTI("accenti", "ACCENTI"),
    CASEI("casei", "CASEI"),
    INDEX("index", "INDEX");

    private final static Map<String, FunctionName> identifiers = new HashMap<>();
    private final String jsonToken;
    private final String textToken;

    FunctionName(String jsonToken, String textToken) {
        this.jsonToken = jsonToken;
        this.textToken = textToken;
    }

    static {
        Arrays.stream(values()).forEach(name -> identifiers.put(name.jsonToken.toLowerCase(Locale.ROOT), name));
    }

    public static Optional<FunctionName> of(String token) {
        return Optional.ofNullable(identifiers.get(token.toLowerCase(Locale.ROOT)));
    }

    public String getJsonToken() {
        return jsonToken;
    }

    public String getTextToken() {
        return textToken;
    }
}
