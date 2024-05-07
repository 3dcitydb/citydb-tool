package org.citydb.web.schema;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "link")
public class Link {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String href;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String rel;
    private String type;
    private String hreflang;
    private String title;
    private int length;

    private Link(String href, String rel) {
        this.href = href;
        this.rel = rel;
    }

    public static Link of (String href, String rel) {
        return new Link(href, rel);
    }

    public String getHref() {
        return href;
    }

    public Link setHref(String href) {
        this.href = href;
        return this;
    }

    public String getRel() {
        return rel;
    }

    public Link setRel(String rel) {
        this.rel = rel;
        return this;
    }

    public String getType() {
        return type;
    }

    public Link setType(String type) {
        this.type = type;
        return this;
    }

    public String getHreflang() {
        return hreflang;
    }

    public Link setHreflang(String hreflang) {
        this.hreflang = hreflang;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Link setTitle(String title) {
        this.title = title;
        return this;
    }

    public int getLength() {
        return length;
    }

    public Link setLength(int length) {
        this.length = length;
        return this;
    }
}
