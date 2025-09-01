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

package org.citydb.cli.util;

import java.io.Console;
import java.util.Optional;
import java.util.function.Supplier;

public class ConsolePrompt {
    private String prompt = "";
    private String defaultValue = "";
    private boolean trimInput;

    private ConsolePrompt() {
    }

    public static ConsolePrompt newInstance() {
        return new ConsolePrompt();
    }

    public ConsolePrompt prompt(String prompt, Object... args) {
        if (prompt != null) {
            this.prompt = args != null && args.length > 0 ? String.format(prompt, args) : prompt;
        }

        return this;
    }

    public ConsolePrompt defaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    public ConsolePrompt trimInput(boolean trimInput) {
        this.trimInput = trimInput;
        return this;
    }

    public PromptResult readPassword() {
        return readLine(true);
    }

    public PromptResult readLine() {
        return readLine(false);
    }

    private PromptResult readLine(boolean hideInput) {
        Console console = System.console();
        if (console != null) {
            String input;
            if (hideInput) {
                char[] chars = console.readPassword(prompt);
                input = chars != null ? new String(chars) : null;
            } else {
                input = console.readLine(prompt);
            }

            if (input != null && trimInput) {
                input = input.trim();
            }

            if (input == null || input.isEmpty()) {
                input = defaultValue;
            }

            return new PromptResult(input, true);
        } else {
            return new PromptResult(null, false);
        }
    }

    public static class PromptResult {
        private final String value;
        private final boolean consoleAvailable;

        PromptResult(String value, boolean consoleAvailable) {
            this.value = value;
            this.consoleAvailable = consoleAvailable;
        }

        public boolean wasConsoleAvailable() {
            return consoleAvailable;
        }

        public Optional<String> get() {
            return consoleAvailable ? Optional.ofNullable(value) : Optional.empty();
        }

        public String orElse(String defaultValue) {
            return consoleAvailable ? value : defaultValue;
        }

        public <T extends Exception> String orElseThrow(Supplier<T> exceptionSupplier) throws T {
            if (!consoleAvailable) {
                throw exceptionSupplier.get();
            }

            return value;
        }
    }
}
