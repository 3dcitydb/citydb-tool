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

package org.citydb.core.version;

import java.util.Objects;

public class Version implements Comparable<Version> {
    private final int major;
    private final int minor;
    private final int revision;
    private final String versionString;

    private Version(int major, int minor, int revision, String versionString) {
        this.major = major;
        this.minor = minor;
        this.revision = revision;
        this.versionString = versionString;
    }

    public static Version of(int major, int minor, int revision, String versionString) {
        return new Version(major, minor, revision, versionString != null ?
                versionString :
                major + "." + minor + "." + revision);
    }

    public static Version of(int major, int minor, int revision) {
        return new Version(major, minor, revision, major + "." + minor + "." + revision);
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

    public String toVersionString() {
        return toVersionString(false);
    }

    public String toVersionString(boolean skipRevision) {
        return skipRevision ?
                major + "." + minor :
                major + "." + minor + "." + revision;
    }

    public int compareTo(Version other, boolean skipRevision) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        } else if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        } else {
            return skipRevision ? 0 : Integer.compare(revision, other.revision);
        }
    }

    @Override
    public int compareTo(Version other) {
        return compareTo(other, false);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, revision);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Version version)) {
            return false;
        } else {
            return compareTo(version) == 0;
        }
    }

    @Override
    public String toString() {
        return versionString;
    }
}
