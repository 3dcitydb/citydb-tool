/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.cityobjectgroup.RoleProperty;
import org.citygml4j.core.model.core.AbstractCityObjectReference;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CityObjectGroupRemover {
    private final ConcurrentLinkedQueue<CityObjectGroup> groups;
    private final Map<String, List<AbstractCityObjectReference>> groupParents = new ConcurrentHashMap<>();
    private final Map<String, List<RoleProperty>> groupMembers = new ConcurrentHashMap<>();
    private final MemberProcessor memberProcessor = new MemberProcessor();

    CityObjectGroupRemover(ConcurrentLinkedQueue<CityObjectGroup> groups) {
        this.groups = groups;
        preprocess();
    }

    Collection<CityObjectGroup> getCityObjectGroups() {
        return groups;
    }

    boolean hasCityObjectGroups() {
        return !groups.isEmpty();
    }

    private void preprocess() {
        if (!groups.isEmpty()) {
            int capacity = Math.min(10, groups.size());

            for (CityObjectGroup group : groups) {
                if (group.getGroupParent() != null && group.getGroupParent().getHref() != null) {
                    String id = FeatureHelper.getIdFromReference(group.getGroupParent().getHref());
                    groupParents.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(group.getGroupParent());
                }

                if (group.isSetGroupMembers()) {
                    for (RoleProperty property : group.getGroupMembers()) {
                        if (property.getObject() != null
                                && property.getObject().getGroupMember() != null
                                && property.getObject().getGroupMember().getHref() != null) {
                            String id = FeatureHelper.getIdFromReference(property.getObject().getGroupMember().getHref());
                            groupMembers.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(property);
                        }
                    }
                }
            }
        }
    }

    public void removeMembers(AbstractFeature feature) {
        if (!groups.isEmpty()) {
            feature.accept(memberProcessor);
        }
    }

    public void postprocess() {
        if (!groups.isEmpty()) {
            groups.removeIf(group -> !group.isSetGroupMembers());
        }
    }

    private class MemberProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (feature.getId() != null) {
                List<AbstractCityObjectReference> references = groupParents.remove(feature.getId());
                if (references != null) {
                    references.forEach(reference -> reference.getParent(CityObjectGroup.class).setGroupParent(null));
                }

                List<RoleProperty> properties = groupMembers.remove(feature.getId());
                if (properties != null) {
                    for (RoleProperty property : properties) {
                        CityObjectGroup group = property.getParent(CityObjectGroup.class);
                        group.getGroupMembers().remove(property);
                        if (!group.isSetGroupMembers()) {
                            visit((AbstractFeature) group);
                        }
                    }
                }
            }
        }
    }
}
