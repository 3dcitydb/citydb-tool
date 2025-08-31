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

import org.citydb.cli.ExecutionException;

import java.io.Console;

public class ConsoleHelper {

    public static boolean hasInteractiveConsole() {
        return System.console() != null;
    }

    public static String readPassword() throws ExecutionException {
        return readInput("", true);
    }

    public static String readPassword(String prompt) throws ExecutionException {
        return readInput(prompt, true);
    }

    public static String readInput() throws ExecutionException {
        return readInput("", false);
    }

    public static String readInput(String prompt) throws ExecutionException {
        return readInput(prompt, false);
    }

    private static String readInput(String prompt, boolean hideInput) throws ExecutionException {
        Console console = System.console();
        if (console == null) {
            throw new ExecutionException("No console available. Input cannot be read interactively.");
        }

        if (hideInput) {
            char[] input = console.readPassword(prompt);
            return input != null ? new String(input) : null;
        } else {
            return console.readLine(prompt);
        }
    }
}
