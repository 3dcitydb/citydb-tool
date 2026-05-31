module org.citydb.plugin {
    requires org.citydb.core;

    exports org.citydb.plugin;
    exports org.citydb.plugin.metadata;

    uses org.citydb.plugin.Plugin;
}