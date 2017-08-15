package net.sergeych.farcall;

import net.sergeych.tools.JsonTool;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by sergeych on 13.04.16.
 */
public class BasicConnector extends JsonTool {
    protected final InputStream in;
    protected final OutputStream out;
    protected final AtomicBoolean closed = new AtomicBoolean(false);
    private boolean autoClosing;

    public BasicConnector(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    public void close() {
        if( closed.getAndSet(true) ) {
            if ( autoClosing ) {
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
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean isAutoClosing() {
        return autoClosing;
    }

    /**
     * Set the autoclosing mode, which closes connected streams when endpoint sends {@link #close()}.
     * @param autoClosing true to close streams on close
     */
    public void setAutoClosing(boolean autoClosing) {
        this.autoClosing = autoClosing;
    }
}
