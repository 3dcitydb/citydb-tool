package org.citydb.web.paging;

import org.citydb.web.schema.geojson.FeatureGeoJSON;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public class PagingManager
        implements PagingAndSortingRepository<FeatureGeoJSON, Long> {

    @Override
    public Iterable<FeatureGeoJSON> findAll(Sort sort) {
        return null;
    }

    @Override
    public Page<FeatureGeoJSON> findAll(Pageable pageable) {
        return null;
    }
}