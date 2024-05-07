package org.citydb.web.schema;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "extent")
public class Extent {
    private ExtentSpatial spatial;

    private Extent(ExtentSpatial spatial) {
        this.spatial = spatial;
    }

    public static Extent of(ExtentSpatial spatial) {
        return new Extent(spatial);
    }
    public ExtentSpatial getSpatial() {
        return spatial;
    }

    public Extent setSpatial(ExtentSpatial spatial) {
        this.spatial = spatial;
        return this;
    }

}
