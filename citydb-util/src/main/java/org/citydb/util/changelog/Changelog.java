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

package org.citydb.util.changelog;

import org.citydb.core.function.Pipeline;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.util.SqlHelper;
import org.citydb.model.change.FeatureChange;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.walker.ModelWalker;
import org.citydb.util.changelog.query.ChangelogQuery;
import org.citydb.util.changelog.query.QueryBuildException;
import org.citydb.util.changelog.query.QueryBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.function.Consumer;

public class Changelog {
    private final DatabaseAdapter adapter;
    private final ChangelogHelper helper;
    private final QueryBuilder builder;
    private final SqlHelper sqlHelper;

    private Changelog(DatabaseAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
        helper = new ChangelogHelper(adapter);
        builder = QueryBuilder.of(adapter, helper);
        sqlHelper = adapter.getSchemaAdapter().getSqlHelper();
    }

    public static Changelog of(DatabaseAdapter adapter) {
        return new Changelog(adapter);
    }

    public void forEachChange(ChangelogQuery query, Consumer<FeatureChange> consumer) throws ChangelogException {
        processChanges(query, Pipeline.forEach(consumer));
    }

    public <R> R processChanges(ChangelogQuery query, Pipeline<FeatureChange, R> pipeline) throws ChangelogException {
        try (Connection connection = adapter.getPool().getConnection(false);
             PreparedStatement stmt = sqlHelper.prepareStatement(builder.buildForChanges(query), connection);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Pipeline.Action action = pipeline.process(helper.getFeatureChange(rs));
                if (action == Pipeline.Action.STOP) {
                    break;
                }
            }

            return pipeline.getResult();
        } catch (QueryBuildException e) {
            throw new ChangelogException("Failed to build changelog query.", e);
        } catch (Exception e) {
            throw new ChangelogException("Failed to query feature changes.", e);
        }
    }

    public void forEachRegion(ChangelogQuery query, Consumer<Polygon> consumer) throws ChangelogException {
        processRegions(query, Pipeline.forEach(consumer));
    }

    public <R> R processRegions(ChangelogQuery query, Pipeline<Polygon, R> pipeline) throws ChangelogException {
        try (Connection connection = adapter.getPool().getConnection(false);
             PreparedStatement stmt = sqlHelper.prepareStatement(builder.buildForRegions(query), connection);
             ResultSet rs = stmt.executeQuery()) {
            boolean[] shouldProcess = {true};
            while (shouldProcess[0] && rs.next()) {
                Geometry<?> geometry = helper.getGeometry(rs.getObject("region"));
                if (geometry != null) {
                    geometry.accept(new ModelWalker() {
                        @Override
                        public void visit(Polygon polygon) {
                            Pipeline.Action action = pipeline.process(polygon);
                            if (action == Pipeline.Action.STOP) {
                                shouldProcess[0] = false;
                            }
                        }
                    });
                }
            }

            return pipeline.getResult();
        } catch (QueryBuildException e) {
            throw new ChangelogException("Failed to build changelog query.", e);
        } catch (Exception e) {
            throw new ChangelogException("Failed to query change regions.", e);
        }
    }
}
