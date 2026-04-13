module org.citydb.vis {
    requires org.citydb.io;
    requires com.alibaba.fastjson2;
    requires org.slf4j;
    requires drako;
    requires texture.atlas.creator;
    requires java.desktop;

    exports org.citydb.vis;
    exports org.citydb.vis.encoder;
    exports org.citydb.vis.geometry;
    exports org.citydb.vis.scene;
    exports org.citydb.vis.store;
    exports org.citydb.vis.writer;
    // Internal I3S serialization POJOs — exported so fastjson2's ASM-generated
    // ObjectWriters (which live in the unnamed module) can load the classes.
    exports org.citydb.vis.model;

    provides org.citydb.io.IOAdapter with org.citydb.vis.I3SAdapter;
}
