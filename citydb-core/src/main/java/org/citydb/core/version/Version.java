/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.core.version;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version implements Comparable<Version> {
    private static final Pattern versionPattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+).*?");

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

    public static Optional<Version> parse(String version) {
        if (version != null) {
            Matcher matcher = versionPattern.matcher(version.trim());
            if (matcher.matches()) {
                return Optional.of(new Version(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)),
                        version));
            }
        }

        return Optional.empty();
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
