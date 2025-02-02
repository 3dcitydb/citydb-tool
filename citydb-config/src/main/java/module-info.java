module org.citydb.config {
    requires transitive org.citydb.core;
    requires transitive com.alibaba.fastjson2;

    exports org.citydb.config;
    exports org.citydb.config.common;
    exports org.citydb.config.encoding;
}