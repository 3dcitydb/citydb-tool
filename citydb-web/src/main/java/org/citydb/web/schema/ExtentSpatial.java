package org.citydb.web.schema;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "extentSpatial")
public class ExtentSpatial {
    @ArraySchema(minItems = 1)
    private List<Bbox> bbox;

    private ExtentSpatial(List<Bbox> bbox) {
        this.bbox = bbox;
    }

    public static ExtentSpatial of(List<Bbox> bbox) {
        return new ExtentSpatial(bbox);
    }

    public List<Bbox> getBbox() {
        return bbox;
    }

    public ExtentSpatial setBbox(List<Bbox> bbox) {
        this.bbox = bbox;
        return this;
    }
}
