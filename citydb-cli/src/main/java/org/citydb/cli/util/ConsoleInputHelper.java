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
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ConsoleInputHelper {
    public static final int DEFAULT_TIMEOUT = 60;

    public static String readPasswordFromConsole(String prompt) throws ExecutionException {
        return readPasswordFromConsole(prompt, DEFAULT_TIMEOUT);
    }

    public static String readPasswordFromConsole(String prompt, int timeout) throws ExecutionException {
        return readInputFromConsole(prompt, true, timeout);
    }

    public static String readInputFromConsole(String prompt) throws ExecutionException {
        return readInputFromConsole(prompt, false);
    }

    public static String readInputFromConsole(String prompt, boolean hideInput) throws ExecutionException {
        return readInputFromConsole(prompt, hideInput, DEFAULT_TIMEOUT);
    }

    public static String readInputFromConsole(String prompt, boolean hideInput, int timeout) throws ExecutionException {
        Console console = System.console();
        if (console != null) {
            if (hideInput) {
                char[] input = console.readPassword(prompt);
                return input != null ? new String(input) : null;
            } else {
                return console.readLine(prompt);
            }
        } else {
            ExecutorService service = Executors.newSingleThreadExecutor();
            try {
                System.out.print(prompt);
                return service.submit(() -> new Scanner(System.in).nextLine())
                        .get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new ExecutionException("Input timed out after " + timeout + " seconds.", e);
            } catch (Exception e) {
                throw new ExecutionException("Failed to read input from console.", e);
            } finally {
                service.shutdownNow();
            }
        }
    }
}
