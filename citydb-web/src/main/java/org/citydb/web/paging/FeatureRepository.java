package org.citydb.web.paging;

import org.citydb.web.schema.geojson.FeatureGeoJSON;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureRepository
        extends PagingAndSortingRepository<FeatureGeoJSON, Long> {

}
