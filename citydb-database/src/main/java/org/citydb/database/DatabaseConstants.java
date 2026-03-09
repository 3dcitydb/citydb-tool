/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
    public static final String ENV_CITYDB_ADMIN_DB = "CITYDB_ADMIN_DATABASE";

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
