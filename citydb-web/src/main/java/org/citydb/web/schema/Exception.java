package org.citydb.web.schema;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "exception")
public class Exception {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
    private String description;

    private Exception(String code) {
        this.code = code;
    }

    public static Exception of(String code) {
        return new Exception(code);
    }

    public String getCode() {
        return code;
    }

    public Exception setCode(String code) {
        this.code = code;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Exception setDescription(String description) {
        this.description = description;
        return this;
    }
}
