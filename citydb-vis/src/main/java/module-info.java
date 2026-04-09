module org.citydb.vis {
    requires org.citydb.io;
    requires com.alibaba.fastjson2;
    requires org.slf4j;
    requires drako;
    requires texture.atlas.creator;
    requires java.desktop;

    exports org.citydb.vis;
    exports org.citydb.vis.geometry;
    exports org.citydb.vis.scene;
    exports org.citydb.vis.writer;

    provides org.citydb.io.IOAdapter with org.citydb.vis.I3SAdapter;
}
