/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
