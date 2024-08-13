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

package org.citydb.query.builder.sql;

import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.lod.LodFilter;
import org.citydb.query.lod.LodFilterMode;
import org.citydb.sqlbuilder.operation.Exists;
import org.citydb.sqlbuilder.query.Select;

public class LodFilterBuilder {
    private final BuilderHelper helper;

    private LodFilterBuilder(BuilderHelper helper) {
        this.helper = helper;
    }

    static LodFilterBuilder of(BuilderHelper helper) {
        return new LodFilterBuilder(helper);
    }

    void build(LodFilter lodFilter, Select select, SqlContext context) throws QueryBuildException {
        select.where(Exists.of(
                helper.getDatabaseAdapter().getSchemaAdapter().getRecursiveLodQuery(
                        lodFilter.getLods(),
                        lodFilter.getMode() == LodFilterMode.AND,
                        lodFilter.getSearchDepth().orElse(Integer.MAX_VALUE),
                        context.getTable())));
    }
}
