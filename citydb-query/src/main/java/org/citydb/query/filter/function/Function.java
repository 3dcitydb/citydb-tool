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

package org.citydb.query.filter.function;

import org.citydb.query.filter.common.*;
import org.citydb.query.filter.operation.BooleanExpression;
import org.citydb.query.filter.operation.NumericExpression;

import java.util.*;

public class Function implements BooleanExpression, CharacterExpression, GeometryExpression, NumericExpression, PatternExpression {
    private final FunctionName name;
    private final List<Argument> arguments;
    private Sign sign;

    private Function(FunctionName name, List<? extends Argument> arguments) {
        this.name = Objects.requireNonNull(name, "The function name must not be null.");
        this.arguments = new ArrayList<>(Objects.requireNonNull(arguments, "The arguments list must not be null."));
    }

    public static Function of(FunctionName name, List<? extends Argument> arguments) {
        return new Function(name, arguments);
    }

    public static Function of(FunctionName name, Argument... arguments) {
        return new Function(name, arguments != null ? Arrays.asList(arguments) : null);
    }

    public static Function noArg(FunctionName name) {
        return new Function(name, Collections.emptyList());
    }

    public FunctionName getName() {
        return name;
    }

    public boolean hasArguments() {
        return !arguments.isEmpty();
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    public Function add(List<? extends Argument> arguments) {
        if (arguments != null && !arguments.isEmpty()) {
            arguments.stream()
                    .filter(Objects::nonNull)
                    .forEach(this.arguments::add);
        }

        return this;
    }

    public Function add(Argument... arguments) {
        return arguments != null ? add(Arrays.asList(arguments)) : this;
    }

    @Override
    public Sign getSign() {
        return sign != null ? sign : Sign.PLUS;
    }

    @Override
    public NumericExpression negate() {
        sign = sign == null ? Sign.MINUS : null;
        return this;
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
