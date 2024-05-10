package org.citydb.web.schema;

import java.util.List;

public class LandingPage {
    private String title;
    private String description;
    private List<Link> links;

    private LandingPage(List<Link> links) {
        this.links = links;
    }

    public static LandingPage of(List<Link> links) {
        return new LandingPage(links);
    }

    public String getTitle() {
        return title;
    }

    public LandingPage setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public LandingPage setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<Link> getLinks() {
        return links;
    }
}
