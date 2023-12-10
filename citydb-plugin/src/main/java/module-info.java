module org.citydb.plugin {
    requires org.citydb.core;
    requires com.alibaba.fastjson2;

    uses org.citydb.plugin.Plugin;

    exports org.citydb.plugin;
    exports org.citydb.plugin.extension;
    exports org.citydb.plugin.metadata;
}