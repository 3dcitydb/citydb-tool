@SuppressWarnings("requires-transitive-automatic")
module org.citydb.io {
    requires transitive org.citydb.core;
    requires transitive org.citydb.model;
    requires java.sql;

    uses org.citydb.io.IOAdapter;

    exports org.citydb.io;
    exports org.citydb.io.reader;
    exports org.citydb.io.util;
    exports org.citydb.io.validator;
    exports org.citydb.io.writer;
    exports org.citydb.io.writer.options;
}