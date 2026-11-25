module org.citydb.plugin {
    requires org.citydb.core;

    exports org.citydb.plugin;
    exports org.citydb.plugin.metadata;

    opens org.citydb.plugin.metadata to tools.jackson.databind;

    uses org.citydb.plugin.Plugin;
}