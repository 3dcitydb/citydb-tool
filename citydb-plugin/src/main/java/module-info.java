module org.citydb.plugin {
    requires org.citydb.core;
    requires com.alibaba.fastjson2;

    exports org.citydb.plugin;
    exports org.citydb.plugin.metadata;

    uses org.citydb.plugin.Plugin;
}