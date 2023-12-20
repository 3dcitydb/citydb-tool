/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.config.configs;

import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ImportConfig {
    private JSONObject readOptions;
    private Map<String, JSONObject> formatOptions;
    private JSONObject operationOptions;

    public JSONObject getReadOptions() {
        return readOptions;
    }

    public ImportConfig setReadOptions(JSONObject options) {
        this.readOptions = options;
        return this;
    }

    public Map<String, JSONObject> getFormatOptions() {
        if (formatOptions == null) {
            formatOptions = new HashMap<>();
        }

        return formatOptions;
    }

    public ImportConfig setFormatOptions(Map<String, JSONObject> options) {
        this.formatOptions = options;
        return this;
    }

    public JSONObject getOperationOptions() {
        return operationOptions;
    }

    public ImportConfig setOperationOptions(JSONObject options) {
        this.operationOptions = options;
        return this;
    }
}
