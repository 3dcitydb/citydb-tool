package org.citydb.web.schema;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema()
public class Bbox {
    @ArraySchema(minItems = 4, maxItems = 6)
    private List<BigDecimal> value;

    private Bbox(List<BigDecimal> value) {
        this.value = value;
    }

    public static Bbox of(List<BigDecimal> value) {
        return new Bbox(value);
    }

    public List<BigDecimal> getValue() {
        return value;
    }

    public Bbox setValue(List<BigDecimal> value) {
        this.value = value;
        return this;
    }
}
