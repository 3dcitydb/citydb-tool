/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.util;

import java.io.*;

public class Streams {

    private Streams() {
    }

    public static InputStream nonClosing(InputStream in) {
        return new FilterInputStream(in) {
            @Override
            public void close() {
            }
        };
    }

    public static OutputStream nonClosing(OutputStream out) {
        return new FilterOutputStream(out) {
            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }
}
