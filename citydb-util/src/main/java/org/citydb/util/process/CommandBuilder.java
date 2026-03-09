/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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

    public ProcessHelper build() {
        return ProcessHelper.of(parts);
    }

    private boolean nonEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}
