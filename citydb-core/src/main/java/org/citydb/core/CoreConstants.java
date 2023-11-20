/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.core;

import java.nio.file.Path;
import java.nio.file.Paths;

public class CoreConstants {
    public static final Path APP_HOME;
    public static final Path WORKING_DIR;

    static {
        String appHomeEnv = System.getenv("APP_HOME");
        if (appHomeEnv == null) {
            appHomeEnv = ".";
        }

        String workingDirEnv = System.getenv("WORKING_DIR");
        if (workingDirEnv == null) {
            workingDirEnv = ".";
        }

        APP_HOME = Paths.get(appHomeEnv).normalize().toAbsolutePath();
        WORKING_DIR = Paths.get(workingDirEnv).normalize().toAbsolutePath();
    }
}
