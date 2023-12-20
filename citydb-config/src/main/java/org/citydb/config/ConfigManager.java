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
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONWriter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class ConfigManager {
    private static final ConfigManager instance = new ConfigManager();

    private ConfigManager() {
    }

    public static ConfigManager getInstance() {
        return instance;
    }

    public Config load(Path path) throws ConfigException {
        Objects.requireNonNull(path, "The path must not be null.");

        try (InputStream inStream = new BufferedInputStream(Files.newInputStream(path))) {
            return JSON.parseObject(new String(inStream.readAllBytes()), Config.class);
        } catch (IOException e) {
            throw new ConfigException("Failed to read the config file " + path + ".", e);
        } catch (JSONException e) {
            throw new ConfigException("Failed to parse the config file " + path + ".", e);
        }
    }

    public void writeTo(Config config, Path target) throws ConfigException {
        Objects.requireNonNull(target, "The target path must not be null.");
        try {
            Files.write(target, JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat).getBytes());
        } catch (IOException e) {
            throw new ConfigException("Failed to write config file to " + target, e);
        }
    }
}
