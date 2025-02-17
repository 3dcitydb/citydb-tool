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

import java.util.*;
import java.util.stream.Collectors;

public class VersionSupport {
    private final Set<VersionPolicy> policies;

    private VersionSupport(Collection<VersionPolicy> policies) {
        Objects.requireNonNull(policies, "The version policies must not be null.");
        if (policies.isEmpty()) {
            throw new IllegalArgumentException("At least one version policy must be specified.");
        }

        this.policies = new HashSet<>(policies);
    }

    public static VersionSupport of(Collection<VersionPolicy> policies) {
        return new VersionSupport(policies);
    }

    public static VersionSupport of(VersionPolicy... policies) {
        return of(policies != null ? List.of(policies) : Collections.emptyList());
    }

    public static Optional<VersionSupport> parse(String versionSupport) {
        if (versionSupport != null) {
            String[] parts = versionSupport.split(",");
            Set<VersionPolicy> policies = Arrays.stream(parts)
                    .map(VersionPolicy::parse)
                    .flatMap(Optional::stream)
                    .collect(Collectors.toSet());
            if (policies.size() == parts.length) {
                return Optional.of(new VersionSupport(policies));
            }
        }

        return Optional.empty();
    }

    public Set<VersionPolicy> getPolicies() {
        return new HashSet<>(policies);
    }

    public VersionSupport addPolicy(VersionPolicy policy) {
        Set<VersionPolicy> policies = getPolicies();
        policies.add(policy);
        return new VersionSupport(policies);
    }

    public boolean isSupported(Version version) {
        return policies.stream().anyMatch(policy -> policy.matches(version));
    }

    private Map<Version, Set<VersionPolicy>> sorted() {
        Map<Version, Set<VersionPolicy>> sorted = new TreeMap<>(Comparator.reverseOrder());
        for (VersionPolicy policy : policies) {
            sorted.computeIfAbsent(policy.isAllowNewerRevisions() ?
                            Version.of(policy.getUpperBound().getMajor(),
                                    policy.getUpperBound().getMinor(),
                                    Integer.MAX_VALUE) :
                            policy.getUpperBound(), v ->
                            new TreeSet<>(Comparator.comparing(VersionPolicy::getLowerBound).reversed()))
                    .add(policy);
        }

        return sorted;
    }

    @Override
    public String toString() {
        return sorted().values().stream()
                .flatMap(Collection::stream)
                .map(Objects::toString)
                .collect(Collectors.joining(", "));
    }
}
