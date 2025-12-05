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

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.query.Selection;

public class ChangelogHelper implements org.citydb.database.util.ChangelogHelper {
    private final OracleAdapter adapter;

    ChangelogHelper(DatabaseAdapter adapter) {
        this.adapter = (OracleAdapter) adapter;
    }

    @Override
    public Select getChangeRegions(Select baseQuery, Selection<?> envelope) {
        return getAndTransformChangeRegions(baseQuery, envelope,
                adapter.getDatabaseMetadata().getSpatialReference().getSRID());
    }

    @Override
    public Select getAndTransformChangeRegions(Select baseQuery, Selection<?> envelope, int srid) {
        //TODO
        return null;
    }

}
