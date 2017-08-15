package net.sergeych.farcall;

import java.io.IOException;
import java.util.Map;

/**
 * Connectors abstract the data transfer between Farcall instances. Connectors
 * must support at least these types which are supported in JSON, e.g. integers,
 * floats, strings, null, arrays and string-object dictionaries. It is very
 * good idea to support somehow the datetime objects, say, as a dictionary with
 * unix time and type information.
 */
@SuppressWarnings("unused")
public interface Connector {
    /**
     * Pack and send farcall data
     *
     * @param data,
     *         can not be null.
     *
     * @throws IOException
     */
    void send(Map<String, Object> data) throws IOException;

    /**
     * Block until the connection is closed or a valid package is received. Connector unpacks
     * the package and returns it.
     *
     * @return unpacked data or null to signal eof state.
     *
     * @throws IOException
     */
    Map<String, Object> receive() throws IOException;

    /**
     * Called by protocol when it is closing, and will not send and receive data anymore.
     * Connector can expilcitly close streams and free resources at this point.
     */
    void close();
}
