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

package org.citydb.logging;

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class LogConstants {
    public static final String DEFAULT_LOG_PATTERN = "[%d{HH:mm:ss} %p] %m%n";
    public static final String DEFAULT_LOG_FILE = "citydb.log";
    public static final String DEFAULT_ROLLING_FILE_SUFFIX = "-%d{yyyy-MM-dd}";
    public static final Marker PLAIN_MARKER = MarkerManager.getMarker("PLAIN");
}
