module org.citydb.config {
    requires com.alibaba.fastjson2;

    exports org.citydb.config;

    opens org.citydb.config to com.alibaba.fastjson2;
}