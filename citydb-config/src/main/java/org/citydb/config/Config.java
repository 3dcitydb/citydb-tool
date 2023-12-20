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

package org.citydb.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class Config {
    private Map<String, JSONObject> configs = new HashMap<>();

    public void setConfigs(Map<String, JSONObject> configs) {
        if (configs != null) {
            this.configs = configs;
        }
    }

    public <T> T getConfig(String name, Class<T> type) throws ConfigException {
        JSONObject options = configs.get(name);
        if (options != null) {
            return JSON.parseObject(options.toString(), type);
        } else {
            throw new ConfigException("Unsupported configuration type '" + type.getName() + "'.");
        }
    }
}
