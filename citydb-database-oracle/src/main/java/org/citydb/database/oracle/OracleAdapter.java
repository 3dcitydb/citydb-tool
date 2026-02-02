/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright © 2025, Oracle and/or its affiliates.
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

package org.citydb.database.oracle;

import oracle.jdbc.OracleDriver;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.adapter.DatabaseType;

import java.util.Properties;

@DatabaseType(name = OracleAdapter.DATABASE_NAME)
public class OracleAdapter extends DatabaseAdapter {
    public static final String DATABASE_NAME = "Oracle";

    @Override
    protected SchemaAdapter createSchemaAdapter(DatabaseAdapter adapter) {
        return new SchemaAdapter(adapter);
    }

    @Override
    protected GeometryAdapter createGeometryAdapter(DatabaseAdapter adapter) {
        return new GeometryAdapter(adapter);
    }

    @Override
    public Class<?> getDriverClass() {
        return OracleDriver.class;
    }

    @Override
    public int getDefaultPort() {
        return 1521;
    }

    @Override
    public String getConnectionString(String host, int port, String database) {
        return "jdbc:oracle:thin:@//" + host + ":" + port + "/" + database;
    }

    @Override
    public void setDefaultConnectionProperties(Properties properties) {
        properties.setProperty("defaultRowPrefetch", "1000");
    }
}