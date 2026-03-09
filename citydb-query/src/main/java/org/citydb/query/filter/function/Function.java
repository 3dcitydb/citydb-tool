/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
