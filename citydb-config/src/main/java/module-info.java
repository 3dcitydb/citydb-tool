module org.citydb.config {
    requires com.alibaba.fastjson2;

    exports org.citydb.config;
    exports org.citydb.config.configs;

    opens org.citydb.config to com.alibaba.fastjson2;
}