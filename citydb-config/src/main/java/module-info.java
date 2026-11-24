module org.citydb.config {
    requires transitive org.citydb.core;

    exports org.citydb.config;
    exports org.citydb.config.common;
    exports org.citydb.config.encoding;

    opens org.citydb.config to tools.jackson.databind;
    opens org.citydb.config.common to tools.jackson.databind;
}