package net.sergeych.tools;

/**
 * Efficient threadsafe blocking LIFO byte buffer. Created by sergeych on 13.04.16.
 */

public class ByteRingBuffer {
    private final byte[] data;
    private final int capacity;
    private final Object access = new Object();

    private int rpos = 0;
    private int wpos = 0;

    public ByteRingBuffer(int capacity) {
        this.capacity = capacity;
        data = new byte[capacity];
    }

    private int advance(int index) {
        return (index + 1) % capacity;
    }

    /**
     * Get the next byte, block until available.
     */
    @SuppressWarnings("unused")
    public int get() {
        synchronized (access) {
            while (rpos == wpos)
                try {
                    access.wait();
                } catch (InterruptedException e) {
                }
            int result = data[rpos] & 0xFF;
            rpos = advance(rpos);
            access.notifyAll();
            return result;
        }
    }


    /**
     * Nonblocking get the next byte.
     *
     * @return -1 of there is no data or the next available byte.
     */
    public int read() {
        synchronized (access) {
            if (rpos == wpos)
                return -1;
            return get();
        }
    }

    /**
     * put single byte. Block until the space is available.
     *
     * @param value
     */
    @SuppressWarnings("unused")
    public void put(int value) {
        synchronized (access) {
            while (advance(wpos) == rpos)
                try {
                    access.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            data[wpos] = (byte) (value & 0xFF);
            wpos = advance(wpos);
            access.notifyAll();
        }
    }

    /**
     * Get several bytes from the queue (size can be any positive). Blocks until specified number of
     * bytes are written.
     */
    public byte[] get(int size) {
        byte[] result = new byte[size];
        for (int i = 0; i < size; i++) {
            result[i] = (byte)get();
        }
        return result;
    }

    /**
     * Put all the bytes to the buffer. Block until the operation is completed.
     *
     * @param buffer
     */
    public void put(byte[] buffer) {
        for (byte b : buffer)
            put(b);
    }

    /**
     * Put all the bytes to the buffer. Blocks until the operation is completed.
     *
     * @param collection
     */
    public <T extends Number> void put(Iterable<T> collection) {
        for (T x : collection) {
            put(x.intValue());
        }
    }

    /**
     * Read bytes from the buffer, blocking untili available, and convert them to the UTF-8 string
     *
     * @param size string exact size
     */
    public String getString(int size) {
        return new String(get(size));
    }


    /**
     * Put string in UTF-8 encoding into the buffer blocking until there is enough space.
     *
     * @param s string to put
     */
    public void put(String s) {
        put(s.getBytes());
    }

    public boolean isEmpty() {
        return rpos == wpos;
    }

    /**
     * Nonblocking read. Reads bytes into specified buffer
     *
     * @param b
     *         buffer to read into
     *
     * @return number of bytes read, 0 if the buffer is empty
     */
    public int read(byte[] b) {
        int count = 0;
        while (count < b.length) {
            int res = read();
            if (res >= 0)
                b[count++] = (byte) res;
            else
                break;
        }
        return count;
    }

    /**
     * Get all data available from the buffer
     * @return
     */
    public byte[] readAll() {
        synchronized (access) {
            int size = getAvailable();
            byte[] result = new byte[size];
            for( int i=0; i<size; i++)
                result[i] = (byte) get();
            return result;
        }
    }

    /**
     * Get number of bytes available to read at the moment of the invocation.
     * @return
     */
    private int getAvailable() {
        int size = wpos - rpos;
        if(size < 0)
            size += capacity;
        return size;
    }
}
