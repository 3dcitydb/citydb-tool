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

package org.citydb.cli;

import java.io.InputStream;
import java.util.Properties;

public class CliConstants {
    public static final String APP_NAME;
    public static final String APP_VERSION;
    public static final String APP_COPYRIGHT_YEAR;
    public static final String APP_VENDOR;
    public static final String APP_COMMAND = "citydb";
    public static final String PLUGINS_DIR = "plugins";

    static {
        try (InputStream stream = CliConstants.class.getResourceAsStream("/org/citydb/cli/application.properties")) {
            Properties properties = new Properties();
            properties.load(stream);
            APP_NAME = properties.getProperty("name");
            APP_VERSION = properties.getProperty("version");
            APP_COPYRIGHT_YEAR = properties.getProperty("copyrightYear");
            APP_VENDOR = properties.getProperty("vendor");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize application properties.", e);
        }
    }
}
