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

package org.citydb.util.process;

import java.util.ArrayList;
import java.util.List;

public class CommandBuilder {
    private final List<String> parts = new ArrayList<>();

    CommandBuilder() {
    }

    CommandBuilder(String command) {
        add(command);
    }

    public CommandBuilder add(String part) {
        if (nonEmpty(part)) {
            parts.add(part);
        }

        return this;
    }

    public CommandBuilder add(long part) {
        return add(String.valueOf(part));
    }

    public CommandBuilder add(double part) {
        return add(String.valueOf(part));
    }

    public CommandBuilder add(boolean part) {
        return add(String.valueOf(part));
    }

    public CommandBuilder add(Object part) {
        return part != null ? add(part.toString()) : this;
    }

    public CommandBuilder addIf(boolean condition, String part) {
        return condition ? add(part) : this;
    }

    public CommandBuilder addIf(boolean condition, long part) {
        return condition ? add(part) : this;
    }

    public CommandBuilder addIf(boolean condition, double part) {
        return condition ? add(part) : this;
    }

    public CommandBuilder addIf(boolean condition, boolean part) {
        return condition ? add(part) : this;
    }

    public CommandBuilder addIf(boolean condition, Object part) {
        return condition ? add(part) : this;
    }

    public CommandBuilder addOption(String option, String value) {
        return nonEmpty(option) && nonEmpty(value) ?
                add(option).add(value) :
                this;
    }

    public CommandBuilder addOption(String option, long value) {
        return nonEmpty(option) ?
                add(option).add(value) :
                this;
    }

    public CommandBuilder addOption(String option, double value) {
        return nonEmpty(option) ?
                add(option).add(value) :
                this;
    }

    public CommandBuilder addOption(String option, boolean value) {
        return nonEmpty(option) ?
                add(option).add(value) :
                this;
    }

    public CommandBuilder addOption(String option, Object value) {
        return nonEmpty(option) && value != null ?
                add(option).add(value) :
                this;
    }

    public CommandBuilder addOptionIf(boolean condition, String option, String value) {
        return condition ? addOption(option, value) : this;
    }

    public CommandBuilder addOptionIf(boolean condition, String option, long value) {
        return condition ? addOption(option, value) : this;
    }

    public CommandBuilder addOptionIf(boolean condition, String option, double value) {
        return condition ? addOption(option, value) : this;
    }

    public CommandBuilder addOptionIf(boolean condition, String option, boolean value) {
        return condition ? addOption(option, value) : this;
    }

    public CommandBuilder addOptionIf(boolean condition, String option, Object value) {
        return condition ? addOption(option, value) : this;
    }

    public String quote(String value) {
        return '"' + value + '"';
    }

    public ProcessHelper build() {
        return ProcessHelper.of(parts);
    }

    private boolean nonEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}
