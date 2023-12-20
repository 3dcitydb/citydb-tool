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
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.HashMap;
import java.util.Map;

public class Config {
    @JSONField(name = "config")
    private Map<String, JSONObject> configs = new HashMap<>();

    public Map<String, JSONObject> getConfigs() {
        return configs;
    }

    public Config setConfig(String name, JSONObject config) {
        configs.put(name, config);
        return this;
    }

    public <T> T getConfig(String name, Class<T> type) throws ConfigException {
        JSONObject options = configs.get(name);
        if (options != null) {
            return JSON.parseObject(options.toString(), type);
        } else {
            throw new ConfigException("Unsupported configuration type '" + type.getName() + "'.");
        }
    }

    public <T> Config setConfig(String name, Object config) {
        Object object = JSON.toJSON(config);
        if (object instanceof JSONObject) {
            configs.put(name, (JSONObject) JSON.toJSON(config));
        }

        return this;
    }

    public Config setConfigs(Map<String, JSONObject> configs) {
        if (configs != null) {
            this.configs = configs;
        }

        return this;
    }
}
