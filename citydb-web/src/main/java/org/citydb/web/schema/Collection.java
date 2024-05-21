package org.citydb.web.schema;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "collection")
public class Collection {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;
    private String title;
    private String description;
    private List<Link> links;
    private Extent extent;
    private String itemType;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> crs;

    private Collection(String id, List<String> crs) {
        this.id = id;
        this.crs = crs;
    }

    public static Collection of(String id) {
        return new Collection(id, new ArrayList<>(List.of("http://www.opengis.net/def/crs/OGC/1.3/CRS84")));
    }

    public static Collection of(String id, List<String> crs) {
        return new Collection(id, crs);
    }

    public Collection setId(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Collection setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Collection setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<Link> getLinks() {
        return links;
    }

    public Collection setLinks(List<Link> links) {
        this.links = links;
        return this;
    }

    public Extent getExtent() {
        return extent;
    }

    public Collection setExtent(Extent extent) {
        this.extent = extent;
        return this;
    }

    public String getItemType() {
        return itemType;
    }

    public Collection setItemType(String itemType) {
        this.itemType = itemType;
        return this;
    }

    public List<String> getCrs() {
        return crs;
    }

    public Collection setCrs(List<String> crs) {
        this.crs = crs;
        return this;
    }
}