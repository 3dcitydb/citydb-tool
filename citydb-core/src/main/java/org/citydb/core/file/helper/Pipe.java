/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.core.file.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Pipe {
    private final Input input = new Input();
    private final Output output = new Output();
    private final byte[] buffer;

    private int position;
    private int size;
    private volatile boolean inputClosed;
    private volatile boolean outputClosed;

    public Pipe() {
        this(8192);
    }

    public Pipe(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be greater than zero.");
        }

        buffer = new byte[size];
    }

    public InputStream sink() {
        return input;
    }

    public OutputStream source() {
        return output;
    }

    private synchronized int read() throws IOException {
        if (!waitForInput()) {
            return -1;
        }

        final int b = buffer[position] & 0xff;
        position = (position + 1) % buffer.length;
        size--;

        if (size + 1 == buffer.length) {
            notifyAll();
        }

        return b;
    }

    private synchronized int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        } else if (inputClosed) {
            throw new IOException("The input stream has been closed.");
        } else if (len == 0) {
            return 0;
        } else if (!waitForInput()) {
            return -1;
        }

        int total = 0;
        int num;
        while ((num = Math.min(len, Math.min(size, buffer.length - position))) > 0) {
            System.arraycopy(buffer, position, b, off, num);
            position = (position + num) % buffer.length;
            size -= num;
            off += num;
            len -= num;
            total += num;
        }

        if (size + total == buffer.length) {
            notifyAll();
        }

        return total;
    }

    private synchronized long skip(long n) throws IOException {
        if (inputClosed) {
            throw new IOException("The input stream has been closed.");
        } else if (n <= 0) {
            return 0;
        }

        final int skip = (int) Math.min(size, n);
        position = (position + skip) % buffer.length;
        size -= skip;

        if (size + skip == buffer.length) {
            notifyAll();
        }

        return skip;
    }

    private synchronized int available() throws IOException {
        if (inputClosed) {
            throw new IOException("The input stream has been closed.");
        }

        return size;
    }

    private synchronized void closeInput() {
        if (!inputClosed) {
            inputClosed = true;
            notifyAll();
        }
    }

    private synchronized void write(int b) throws IOException {
        waitForOutput();

        buffer[(position + size) % buffer.length] = (byte) b;
        size++;

        if (size == 1) {
            notifyAll();
        }
    }

    private synchronized void write(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || off + len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        } else if (outputClosed) {
            throw new IOException("The output stream has been closed.");
        }

        while (len > 0) {
            waitForOutput();

            final int index = (position + size) % buffer.length;
            final int num = Math.min(len, (index < position ? position : buffer.length) - index);
            System.arraycopy(b, off, buffer, index, num);
            size += num;
            off += num;
            len -= num;

            if (size == num) {
                notifyAll();
            }
        }
    }

    private synchronized void closeOutput() {
        if (!outputClosed) {
            outputClosed = true;
            notifyAll();
        }
    }

    private boolean waitForInput() throws IOException {
        while (true) {
            if (inputClosed) {
                throw new IOException("The input stream has been closed.");
            } else if (size > 0) {
                return true;
            } else if (outputClosed) {
                return false;
            }

            try {
                wait();
            } catch (InterruptedException e) {
                throw new IOException("Stream pipe has been interrupted.");
            }
        }
    }

    private void waitForOutput() throws IOException {
        while (true) {
            if (outputClosed) {
                throw new IOException("The output stream has been closed.");
            } else if (inputClosed) {
                throw new IOException("The input stream has been closed.");
            } else if (size < buffer.length) {
                return;
            }

            try {
                wait();
            } catch (InterruptedException e) {
                throw new IOException("Stream pipe has been interrupted.");
            }
        }
    }

    private class Input extends InputStream {

        @Override
        public int read() throws IOException {
            return Pipe.this.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return Pipe.this.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return Pipe.this.skip(n);
        }

        @Override
        public int available() throws IOException {
            return Pipe.this.available();
        }

        @Override
        public void close() {
            Pipe.this.closeInput();
        }
    }

    private class Output extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            Pipe.this.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            Pipe.this.write(b, off, len);
        }

        @Override
        public void close() {
            Pipe.this.closeOutput();
        }
    }
}
