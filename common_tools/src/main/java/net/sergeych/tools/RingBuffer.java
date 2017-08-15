package net.sergeych.tools;

import java.util.ArrayList;

/**
 * Created by sergeych on 14.04.16.
 */
//@SuppressWarnings("unused")
public class RingBuffer<T> {
    private final Object[] data;
    private final int capacity;
    private final Object access = new Object();

    private int rpos = 0;
    private int wpos = 0;

    public RingBuffer(int capacity) {
        this.capacity = capacity;
        data = new Object[capacity];
    }

    private int advance(int index) {
        return (index + 1) % capacity;
    }

    /**
     * Get the next object, block until available.
     */
    public T get() {
        synchronized (access) {
            while (rpos == wpos)
                try {
                    access.wait();
                } catch (InterruptedException e) {
                }
            Object result = data[rpos];
            rpos = advance(rpos);
            access.notifyAll();
            return (T) result;
        }
    }


    /**
     * Nonblocking get the next object
     *
     * @return  null of there is no data or the next available object
     */
    public T read() {
        synchronized (access) {
            if (rpos == wpos)
                return null;
            return get();
        }
    }

    /**
     * put object, block until there is space available.
     *
     * @param value object to put
     */
    public void put(Object value) {
        synchronized (access) {
            while (advance(wpos) == rpos)
                try {
                    access.wait();
                } catch (InterruptedException e) {
                }
            data[wpos] = value;
            wpos = advance(wpos);
            access.notifyAll();
        }
    }

    /**
     * Put all the bytes to the buffer. Blocks until the operation is completed.
     *
     * @param collection
     */
    public void put(Iterable<T> collection) {
        for (T x : collection) {
            put(x);
        }
    }

    public boolean isEmpty() {
        return rpos == wpos;
    }


    /**
     * Get all data available from the buffer
     * @return
     */
    public ArrayList<T> readAll() {
        synchronized (access) {
            int size = getAvailable();
            ArrayList<T> result = new ArrayList<>();
            for( int i=0; i<size; i++)
                result.add(get());
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
