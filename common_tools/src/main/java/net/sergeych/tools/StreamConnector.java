package net.sergeych.tools;

import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Privides pair or connected streams.
 *
 * Created by sergeych on 13.04.16.
 */
@SuppressWarnings("unused")
public class StreamConnector {

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    private final InputStream in;
    private final OutputStream out;
    private final ByteRingBuffer buffer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public StreamConnector() {
        this(512);
    }

    public StreamConnector(int bufferSize) {
        buffer = new ByteRingBuffer(bufferSize);
        in = new InputStream() {
            @Override
            public int read() throws IOException {
                if( closed.get() ) {
                    close();
                    return -1;
                }
                return buffer.get();
            }

            @Override
            public void close() throws IOException {
                super.close();
                StreamConnector.this.close();
            }
        };
        out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                buffer.put(b);
            }
        };
    }

    public void close() {
        if( !closed.getAndSet(true) ) {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void dump() {
        new Bytes(buffer.readAll()).dump();
    }
}
