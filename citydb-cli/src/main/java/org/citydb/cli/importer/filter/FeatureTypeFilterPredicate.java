/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.filter;

import org.citydb.database.schema.FeatureType;
import org.citydb.database.schema.SchemaMapping;
import org.citydb.io.reader.filter.FilterException;
import org.citydb.io.reader.filter.FilterPredicate;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.PrefixedName;
import org.citydb.model.feature.Feature;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeatureTypeFilterPredicate implements FilterPredicate {
    private final Set<Name> featureTypes;

    private FeatureTypeFilterPredicate(Set<Name> featureTypes) {
        this.featureTypes = featureTypes;
    }

    static FeatureTypeFilterPredicate of(List<PrefixedName> typeNames, SchemaMapping schemaMapping) throws FilterException {
        Set<Name> featureTypes = new HashSet<>();
        for (PrefixedName typeName : typeNames) {
            FeatureType featureType = schemaMapping.getFeatureType(typeName);
            if (featureType == FeatureType.UNDEFINED
                    && typeName.getPrefix().isEmpty()
                    && typeName.getNamespace().equals(Namespaces.EMPTY_NAMESPACE)) {
                featureType = schemaMapping.getFeatureTypes().stream()
                        .filter(type -> type.getName().getLocalName().equalsIgnoreCase(typeName.getLocalName()))
                        .findFirst().orElse(FeatureType.UNDEFINED);
            }

            if (featureType == FeatureType.UNDEFINED) {
                throw new FilterException("The feature type '" + typeName + "' is undefined.");
            } else {
                featureTypes.add(featureType.getName());
            }
        }

        return new FeatureTypeFilterPredicate(featureTypes);
    }

    @Override
    public boolean test(Feature feature) {
        return featureTypes.contains(feature.getFeatureType());
    }
}
