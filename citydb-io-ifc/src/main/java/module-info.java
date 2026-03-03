module org.citydb.io.ifc {
    requires org.citydb.io;
    requires org.opensourcebim.bimserver;
    requires com.google.gson;
    requires org.slf4j;
    requires org.geotools.api;
    requires org.geotools.referencing;

    exports org.citydb.io.ifc;
    exports org.citydb.io.ifc.reader;
    exports org.citydb.io.ifc.converter;

    provides org.citydb.io.IOAdapter with org.citydb.io.ifc.IfcAdapter;
}
