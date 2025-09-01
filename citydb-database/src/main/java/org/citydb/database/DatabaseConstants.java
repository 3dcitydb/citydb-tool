/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.database;

import org.citydb.core.version.VersionPolicy;
import org.citydb.core.version.VersionSupport;

import java.io.InputStream;
import java.util.Properties;

public class DatabaseConstants {
    public static final String CITYDB_NAME;
    public static final String CITYDB_SHORT_NAME;

    public static final String ENV_CITYDB_HOST = "CITYDB_HOST";
    public static final String ENV_CITYDB_PORT = "CITYDB_PORT";
    public static final String ENV_CITYDB_NAME = "CITYDB_NAME";
    public static final String ENV_CITYDB_SCHEMA = "CITYDB_SCHEMA";
    public static final String ENV_CITYDB_USERNAME = "CITYDB_USERNAME";
    public static final String ENV_CITYDB_PASSWORD = "CITYDB_PASSWORD";
    public static final String ENV_CITYDB_CONN_PROPS = "CITYDB_CONN_PROPS";
    public static final String ENV_CITYDB_ADMIN_USERNAME = "CITYDB_ADMIN_USERNAME";
    public static final String ENV_CITYDB_ADMIN_PASSWORD = "CITYDB_ADMIN_PASSWORD";

    public static final VersionSupport VERSION_SUPPORT = VersionSupport.of(
            VersionPolicy.from(5, 0, 0).to(5, 1)
    );

    static {
        try (InputStream stream = DatabaseConstants.class.getResourceAsStream("/org/citydb/database/database.properties")) {
            Properties properties = new Properties();
            properties.load(stream);
            CITYDB_NAME = properties.getProperty("citydbName");
            CITYDB_SHORT_NAME = properties.getProperty("citydbShortName");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database properties.", e);
        }
    }
}
