module org.citydb.io {
    requires java.sql;
    requires transitive org.citydb.config;
    requires transitive org.citydb.core;
    requires transitive org.citydb.model;

    exports org.citydb.io;
    exports org.citydb.io.reader;
    exports org.citydb.io.reader.filter;
    exports org.citydb.io.reader.options;
    exports org.citydb.io.util;
    exports org.citydb.io.validator;
    exports org.citydb.io.writer;
    exports org.citydb.io.writer.options;

    uses org.citydb.io.IOAdapter;
}