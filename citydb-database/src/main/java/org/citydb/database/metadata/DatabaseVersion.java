/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.database.metadata;

public class DatabaseVersion {
    private final int major;
    private final int minor;
    private final int revision;
    private final String versionString;

    private DatabaseVersion(int major, int minor, int revision, String versionString) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.versionString = versionString;
    }

    public static DatabaseVersion of(int major, int minor, int revision, String versionString) {
        return new DatabaseVersion(major, minor, revision, versionString != null ?
                versionString :
                major + "." + minor + "." + revision);
    }

    public static DatabaseVersion of(int major, int minor, int revision) {
        return new DatabaseVersion(major, minor, revision, major + "." + minor + "." + revision);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getRevision() {
        return revision;
    }

    public String getVersionString() {
        return versionString;
    }

    @Override
    public String toString() {
        return versionString;
    }
}
