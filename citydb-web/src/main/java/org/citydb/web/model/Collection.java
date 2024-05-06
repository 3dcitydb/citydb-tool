package org.citydb.web.model;

public class Collection {
    private String id;

    private String title;

    private String description;

    public Collection() {

    }

    public Collection(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "Collection [id=" + id + ", title=" + title + ", desc=" + description + "]";
    }
}