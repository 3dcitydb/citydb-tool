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
import org.citydb.query.builder.common.Type;
import org.citydb.query.filter.function.Function;
import org.citydb.query.filter.function.FunctionName;
import org.citydb.sqlbuilder.literal.ScalarExpression;
import org.citydb.sqlbuilder.query.Select;

public class FunctionBuilder {
    private final FilterBuilder filterBuilder;
    private final BuilderHelper helper;

    private FunctionBuilder(FilterBuilder filterBuilder, BuilderHelper helper) {
        this.filterBuilder = filterBuilder;
        this.helper = helper;
    }

    static FunctionBuilder of(FilterBuilder filterBuilder, BuilderHelper helper) {
        return new FunctionBuilder(filterBuilder, helper);
    }

    BuildResult build(Function function, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        return switch (function.getName()) {
            case ACCENTI -> accenti(function, select, context, negate);
            case CASEI -> casei(function, select, context, negate);
            case INDEX -> index(function, context);
        };
    }

    private BuildResult accenti(Function function, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        requireArguments(1, function);
        BuildResult argument = filterBuilder.build(function.getArguments().get(0), select, context, negate);

        argument.cast(Type.STRING, helper);
        requireType(argument, Type.STRING, function.getName());

        if (argument.getExpression() instanceof ScalarExpression scalarExpression) {
            return BuildResult.of(helper.getOperationHelper().unaccent(scalarExpression),
                    Type.STRING, argument);
        } else {
            throw new QueryBuildException("Failed to build accenti function.");
        }
    }

    private BuildResult casei(Function function, Select select, SqlContext context, boolean negate) throws QueryBuildException {
        requireArguments(1, function);
        BuildResult argument = filterBuilder.build(function.getArguments().get(0), select, context, negate);

        argument.cast(Type.STRING, helper);
        requireType(argument, Type.STRING, function.getName());

        if (argument.getExpression() instanceof ScalarExpression scalarExpression) {
            return BuildResult.of(helper.getOperationHelper().lower(scalarExpression),
                    Type.STRING, argument);
        } else {
            throw new QueryBuildException("Failed to build casei function");
        }
    }

    private BuildResult index(Function function, SqlContext context) throws QueryBuildException {
        requireArguments(0, function);
        if (context.getTable().isLateral()) {
            return BuildResult.of(context.getTable().column("index"), Type.INTEGER);
        } else {
            throw new QueryBuildException("Failed to build index function because of missing lateral join.");
        }
    }

    private void requireArguments(int number, Function function) throws QueryBuildException {
        if (number == 0 && function.hasArguments()) {
            throw new QueryBuildException("The " + function.getName().getJsonToken() + " function " +
                    "does not accept arguments.");
        } else if (number == 1 && function.getArguments().size() != 1) {
            throw new QueryBuildException("The " + function.getName().getJsonToken() + " function " +
                    "requires exactly one argument but found " + function.getArguments().size() + ".");
        } else if (number != function.getArguments().size()) {
            throw new QueryBuildException("The " + function.getName().getJsonToken() + " function " +
                    "requires " + number + " arguments but found " + function.getArguments().size() + ".");
        }
    }

    private void requireType(BuildResult argument, Type type, FunctionName name) throws QueryBuildException {
        argument.requireType(type, () -> "A value of type '" + argument.getType() +
                "' cannot be used as argument of the " + name.getJsonToken() + " function.");
    }
}
