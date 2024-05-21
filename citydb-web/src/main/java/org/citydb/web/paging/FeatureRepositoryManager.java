package org.citydb.web.paging;

import org.citydb.web.schema.geojson.FeatureGeoJSON;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

public class FeatureRepositoryManager implements FeatureRepository {

    @Override
    public Iterable<FeatureGeoJSON> findAll(@NonNull Sort sort) {
        return new ArrayList<>();
    }

    @Override
    public Page<FeatureGeoJSON> findAll(@NonNull Pageable pageable) {
        return null;
    }
}