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

public class VersionPolicy {
    private final Version lowerBound;
    private final Version upperBound;
    private final boolean allowNewerRevisions;

    private VersionPolicy(Version lowerBound, Version upperBound, boolean allowNewerRevisions) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.allowNewerRevisions = allowNewerRevisions;
    }

    private VersionPolicy(Version lowerBound, Version upperBound) {
        this(lowerBound, upperBound, false);
    }

    public static VersionPolicy at(int major, int minor) {
        return at(major, minor, 0).allowNewerRevisions();
    }

    public static VersionPolicy at(int major, int minor, int revision) {
        Version version = Version.of(major, minor, revision);
        return new VersionPolicy(version, version);
    }

    public static VersionPolicyBuilder from(int major, int minor, int revision) {
        return new VersionPolicyBuilder(Version.of(major, minor, revision));
    }

    public VersionPolicy allowNewerRevisions() {
        return allowNewerRevisions ? this : new VersionPolicy(lowerBound, upperBound, true);
    }

    public Version getLowerBound() {
        return lowerBound;
    }

    public Version getUpperBound() {
        return upperBound;
    }

    public boolean isAllowNewerRevisions() {
        return allowNewerRevisions;
    }

    public boolean matches(Version version) {
        if (lowerBound.equals(upperBound)) {
            return allowNewerRevisions ?
                    version.compareTo(lowerBound, true) == 0 && version.getRevision() >= lowerBound.getRevision() :
                    version.equals(lowerBound);
        } else {
            return version.compareTo(lowerBound) >= 0
                    && version.compareTo(upperBound, true) <= 0
                    && (allowNewerRevisions || version.getRevision() <= upperBound.getRevision());
        }
    }

    private String toVersionString(Version version) {
        return allowNewerRevisions ?
                version.toVersionString() + "+" :
                version.toVersionString();
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof VersionPolicy policy)) {
            return false;
        } else {
            return toString().equals(policy.toString());
        }
    }

    @Override
    public String toString() {
        return lowerBound.compareTo(upperBound, allowNewerRevisions) == 0 ?
                toVersionString(lowerBound) :
                lowerBound.toVersionString() + " - " + toVersionString(upperBound);
    }

    public static class VersionPolicyBuilder {
        private final Version lower;

        private VersionPolicyBuilder(Version lower) {
            this.lower = lower;
        }

        public VersionPolicy to(int major, int minor) {
            return lower.getMajor() == major && lower.getMinor() == minor ?
                    to(Version.of(major, minor, lower.getRevision()), true) :
                    to(Version.of(major, minor, 0), true);
        }

        public VersionPolicy to(int major, int minor, int revision) {
            return to(Version.of(major, minor, revision), false);
        }

        private VersionPolicy to(Version upper, boolean allowNewerRevision) {
            if (upper.compareTo(lower) < 0) {
                throw new IllegalArgumentException("Invalid version range: " + lower + " > " +
                        upper.toVersionString(allowNewerRevision) + ".");
            }

            return allowNewerRevision ?
                    new VersionPolicy(lower, upper).allowNewerRevisions() :
                    new VersionPolicy(lower, upper);
        }
    }
}
