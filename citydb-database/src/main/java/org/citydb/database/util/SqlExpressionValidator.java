/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlExpressionValidator {
    private static final List<Pattern> DEFAULT_PATTERNS = List.of(
            Pattern.compile("delete_.+?\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("cleanup_.+?\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("change_.+?\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("set_.+?\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
            Pattern.compile("update_.+?\\(.*?\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

    private final List<Pattern> patterns;

    private SqlExpressionValidator(List<Pattern> patterns) {
        this.patterns = new ArrayList<>(patterns);
    }

    public static SqlExpressionValidator defaults() {
        return new SqlExpressionValidator(DEFAULT_PATTERNS);
    }

    public SqlExpressionValidator withIllegalPattern(Pattern pattern) {
        if (pattern != null) {
            this.patterns.add(pattern);
        }

        return this;
    }

    public SqlExpressionValidator withIllegalPattern(String pattern) {
        if (pattern != null) {
            this.patterns.add(Pattern.compile(pattern));
        }

        return this;
    }

    public <E extends Exception> void validate(String sqlExpression, Function<String, E> exceptionProvider) throws E {
        String illegal = validate(sqlExpression);
        if (illegal != null) {
            throw exceptionProvider.apply(illegal);
        }
    }

    public boolean isValid(String sqlExpression) {
        return validate(sqlExpression) == null;
    }

    private String validate(String sqlExpression) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(sqlExpression);
            if (matcher.find()) {
                return matcher.group();
            }
        }

        return null;
    }
}
