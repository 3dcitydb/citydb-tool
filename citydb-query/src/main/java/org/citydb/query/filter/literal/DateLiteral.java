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

package org.citydb.query.filter.literal;

import org.citydb.query.filter.common.FilterVisitor;
import org.citydb.query.filter.common.ScalarExpression;

import java.time.LocalDate;
import java.util.Optional;

public class DateLiteral extends Literal<LocalDate> implements ScalarExpression {

    private DateLiteral(LocalDate value) {
        super(value);
    }

    public static DateLiteral of(LocalDate value) {
        return new DateLiteral(value);
    }

    public static DateLiteral of(Object value, LocalDate defaultValue) {
        return of(value).orElse(new DateLiteral(defaultValue));
    }

    public static Optional<DateLiteral> of(Object value) {
        if (value instanceof LocalDate localDate) {
            return Optional.of(new DateLiteral(localDate));
        } else {
            try {
                return Optional.of(new DateLiteral(LocalDate.parse(String.valueOf(value))));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    @Override
    public void accept(FilterVisitor visitor) {
        visitor.visit(this);
    }
}
