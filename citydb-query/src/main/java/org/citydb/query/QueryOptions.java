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

package org.citydb.query;

import org.citydb.config.SerializableConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@SerializableConfig(name = "queryOptions")
public class QueryOptions {
    public static final String EXPORT_QUERY = "export";
    public static final String DELETE_QUERY = "delete";
    private Map<String, Query> queries;

    public Map<String, Query> getQueries() {
        if (queries == null) {
            queries = new HashMap<>();
        }

        return queries;
    }

    public Optional<Query> getQuery(String name) {
        return queries != null ?
                Optional.ofNullable(getQueries().get(name)) :
                Optional.empty();
    }

    public QueryOptions setQueries(Map<String, Query> queries) {
        this.queries = queries;
        return this;
    }

    public QueryOptions addQuery(String name, Query query) {
        if (query != null) {
            getQueries().put(name, query);
        }

        return this;
    }
}
