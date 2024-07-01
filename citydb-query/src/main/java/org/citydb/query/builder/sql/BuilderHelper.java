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

import org.citydb.config.common.SrsReference;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.SrsParseException;
import org.citydb.database.metadata.SpatialReference;
import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.database.util.OperationHelper;
import org.citydb.database.util.SpatialOperationHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.geometry.SpatialObject;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.schema.SchemaPathBuilder;
import org.citydb.query.filter.common.FilterWalker;
import org.citydb.query.filter.common.GeometryExpression;
import org.citydb.query.filter.literal.BBoxLiteral;
import org.citydb.query.filter.literal.GeometryLiteral;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;
import org.citydb.sqlbuilder.util.AliasGenerator;
import org.citydb.sqlbuilder.util.DefaultAliasGenerator;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BuilderHelper {
    private final DatabaseAdapter adapter;
    private final SchemaPathBuilder schemaPathBuilder;
    private final FeatureTypesBuilder featureTypesBuilder;
    private final FilterBuilder filterBuilder;
    private final SqlContextBuilder contextBuilder;
    private final AliasGenerator aliasGenerator = DefaultAliasGenerator.newInstance();

    private BuilderHelper(DatabaseAdapter adapter) {
        this.adapter = Objects.requireNonNull(adapter, "The database adapter must not be null.");
        schemaPathBuilder = SchemaPathBuilder.of(adapter);
        featureTypesBuilder = FeatureTypesBuilder.of(this);
        filterBuilder = FilterBuilder.of(this);
        contextBuilder = SqlContextBuilder.of(filterBuilder, this);
    }

    static BuilderHelper of(DatabaseAdapter adapter) {
        return new BuilderHelper(adapter);
    }

    DatabaseAdapter getDatabaseAdapter() {
        return adapter;
    }

    SchemaPathBuilder getSchemaPathBuilder() {
        return schemaPathBuilder;
    }

    FeatureTypesBuilder getFeatureTypesBuilder() {
        return featureTypesBuilder;
    }

    FilterBuilder getFilterBuilder() {
        return filterBuilder;
    }

    SqlContextBuilder getContextBuilder() {
        return contextBuilder;
    }

    OperationHelper getOperationHelper() {
        return adapter.getSchemaAdapter().getOperationHelper();
    }

    SpatialOperationHelper getSpatialOperationHelper() {
        return adapter.getGeometryAdapter().getSpatialOperationHelper();
    }

    SchemaMapping getSchemaMapping() {
        return adapter.getSchemaAdapter().getSchemaMapping();
    }

    public AliasGenerator getAliasGenerator() {
        return aliasGenerator;
    }

    Table getTable(org.citydb.database.schema.Table table) {
        return getTable(table.getName());
    }

    Table getTable(String tableName) {
        return Table.of(tableName, adapter.getConnectionDetails().getSchema(), aliasGenerator);
    }

    boolean matches(org.citydb.database.schema.Table schemaTable, Table table) throws QueryBuildException {
        Table candidate = table.isLateral() ?
                table.getQueryExpression()
                    .filter(Select.class::isInstance)
                    .map(Select.class::cast)
                    .map(select -> select.getFrom().get(0))
                    .orElseThrow(() -> new QueryBuildException("Failed to access inner table of lateral query.")) :
                table;

        return schemaTable.getName().equals(candidate.getName());
    }

    Set<FeatureType> getFeatureTypes(Query query) throws QueryBuildException {
        if (query.hasFeatureTypes()) {
            Set<FeatureType> featureTypes = new HashSet<>();
            for (PrefixedName name : query.getFeatureTypes()) {
                FeatureType featureType = getSchemaMapping().getFeatureType(name);
                if (featureType == FeatureType.UNDEFINED
                        && name.getPrefix().isEmpty()
                        && name.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
                    featureType = getSchemaMapping().getFeatureTypes().stream()
                            .filter(candidate -> candidate.getName().getLocalName().equals(name.getLocalName()))
                            .findFirst().orElse(FeatureType.UNDEFINED);
                }

                if (featureType == FeatureType.UNDEFINED) {
                    throw new QueryBuildException("The feature type '" + name + "' is undefined.");
                } else if (featureType.getTable() != org.citydb.database.schema.Table.FEATURE) {
                    throw new QueryBuildException("The feature type '" + name + "' is not supported in queries.");
                } else {
                    featureTypes.add(featureType);
                }
            }

            return featureTypes;
        } else {
            return getSchemaMapping().getFeatureTypes().stream()
                    .filter(featureType -> featureType.isTopLevel()
                            && featureType.getTable() == org.citydb.database.schema.Table.FEATURE)
                    .collect(Collectors.toSet());
        }
    }

    int getNamespaceId(String namespace) {
        return getSchemaMapping().getNamespaceByURI(namespace).getId();
    }

    SpatialReference getSpatialReference(SrsReference reference) throws QueryBuildException {
        if (reference != null) {
            try {
                if (reference.getSRID().isPresent()) {
                    return adapter.getGeometryAdapter().getSpatialReference(
                            reference.getSRID().get(), reference.getIdentifier().orElse(null));
                } else if (reference.getIdentifier().isPresent()) {
                    return adapter.getGeometryAdapter().getSpatialReference(reference.getIdentifier().get());
                }
            } catch (SrsParseException | SQLException e) {
                throw new QueryBuildException("The requested filter SRS is not supported.", e);
            }
        }

        return adapter.getDatabaseMetadata().getSpatialReference();
    }

    SpatialObject getSpatialLiteral(GeometryExpression expression) {
        SpatialObject[] spatialObject = new SpatialObject[1];
        expression.accept(new FilterWalker() {
            @Override
            public void visit(BBoxLiteral literal) {
                spatialObject[0] = literal.getValue();
            }

            @Override
            public void visit(GeometryLiteral literal) {
                spatialObject[0] = literal.getValue();
            }
        });

        return spatialObject[0];
    }

    int getOrSetSRID(SpatialObject object, SpatialReference filterSrs) {
        int srid = object.getSRID().orElse(0);
        if (srid == 0) {
            srid = filterSrs.getSRID();
            object.setSRID(srid);
        }

        return srid;
    }

    void cast(BuildResult leftOperand, BuildResult rightOperand) throws QueryBuildException {
        rightOperand.cast(leftOperand.getType(), this);
        leftOperand.cast(rightOperand.getType(), this);
    }

    void cast(List<BuildResult> operands) throws QueryBuildException {
        for (int i = 0; i < operands.size(); i++) {
            cast(operands.get(i), operands.get(i < operands.size() - 1 ? i + 1 : 0));
        }
    }
}
