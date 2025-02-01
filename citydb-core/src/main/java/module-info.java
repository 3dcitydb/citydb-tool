@SuppressWarnings("requires-transitive-automatic")
module org.citydb.core {
    requires org.apache.commons.compress;
    requires org.apache.commons.io;
    requires com.h2database.mvstore;
    requires transitive com.alibaba.fastjson2;
    requires transitive org.apache.tika.core;

    exports org.citydb.core;
    exports org.citydb.core.cache;
    exports org.citydb.core.concurrent;
    exports org.citydb.core.file;
    exports org.citydb.core.file.input;
    exports org.citydb.core.file.output;
    exports org.citydb.core.function;
    exports org.citydb.core.time;
    exports org.citydb.core.tuple;
}