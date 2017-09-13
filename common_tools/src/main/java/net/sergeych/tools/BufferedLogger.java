package net.sergeych.tools;


import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.*;

/**
 * Navigable fast asynchronous buffered logger, thread safe. It's main features are:
 * <p>
 * - it does not block caller on {@link #log(String)} using daemon thread to queue all logging requests
 * <p>
 * - it holds lasst records in memory from where it could be easily obtained using {@link #slice(long, int)}, {@link
 * #getLast(int)} and {@link #getCopy()} calls.
 * <p>
 * It is possible to connect a logger to a {@link PrintStream} using {@link #printTo(PrintStream)}.
 */
public class BufferedLogger {


    /**
     * Log entry structure provides ID to navigate, creation instant and the message.
     */
    public static class Entry implements Comparable<Entry> {

        static private AtomicLong serial = new AtomicLong(System.currentTimeMillis());

        public final long id;
        public final Instant instant = Instant.now();
        public final String message;

        private Entry(String message) {
            id = serial.getAndIncrement();
            this.message = message;
        }

        private Entry(long id) {
            this.id = id;
            message = "";
        }

        @Override
        public String toString() {
            return "" + fmt.format(instant) + " " + message;
        }

        /**
         * Default comparator uses {@link #id} as the natural order, not the instant. Usually it is the same
         * order but there is no guarantees.
         *
         * @param e
         *
         * @return
         */
        @Override
        public int compareTo(Entry e) {
            return Long.compare(id, e.id);
        }
    }

    static DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault());
    private PrintStream printStream;

    private int maxLines;
    private final LinkedList<Entry> buffer = new LinkedList<>();
    private PrintStream pstream = null;
    private final BlockingQueue<Entry> queue = new LinkedBlockingQueue<>();
    private final Object queueEmpty = new Object();

    /**
     * Create buffered logger capable to hold in mempry up to specified number of entries, the excessive records will be
     * purged automatically.
     *
     * @param maxEntries
     */
    public BufferedLogger(int maxEntries) {
        this.maxLines = maxEntries;
        Thread t = new Thread(() -> {
            while (true) {
                Entry entry = null;
                if (queue.size() == 0) {
                    synchronized (queueEmpty) {
                        queueEmpty.notifyAll();
                    }
                }
                try {
                    entry = queue.take();
                } catch (InterruptedException e) {
                    return;
                }
                synchronized (buffer) {
                    buffer.add(entry);
                    while (buffer.size() > maxLines)
                        buffer.poll();
                }
                if (printStream != null)
                    printStream.println(entry.toString());
            }
        });
        t.setName("buffered_logger_" + this);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Wait until all queued logging operation are finished. See {@link #log(String)} fro details.
     *
     * @throws InterruptedException
     */
    public void flush() throws InterruptedException {
        synchronized (queueEmpty) {
            if (queue.size() == 0)
                return;
            queueEmpty.wait();
        }
    }

    /**
     * Wait for log messages queue emptied up to specified number of milliseconds
     *
     * @param millis to stop waiting after
     *
     * @return true if the messages queue is emptied, false if timeout is expired
     */
    public boolean flush(long millis) {
        synchronized (queueEmpty) {
            if (queue.size() == 0)
                return true;
            try {
                queueEmpty.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return queue.size() == 0;
        }
    }

    /**
     * Copy results to a given PrintStream (to be used with System.out, for example)
     *
     * @param ps printStream to print to. Use null to cancel.
     *
     * @return null or previously associated PrintStream
     */
    public PrintStream printTo(PrintStream ps) {
        PrintStream old = printStream;
        printStream = ps;
        return old;
    }

    /**
     * Add log entry to the buffer.
     *
     * @param message
     *
     * @return entry created for the log message
     */
    public @NonNull Entry log(String message) {
        Entry entry = new Entry(message);
        queue.add(entry);
        return entry;
    }

    /**
     * Return most recent record up to specified number of entries.
     *
     * @param maxEntries must be > 0. If current number of records is less than specified, returns all.
     *
     * @return List
     */
    public @NonNull List<Entry> getLast(int maxEntries) {
        ArrayList<Entry> results = new ArrayList<>(maxEntries);
        synchronized (buffer) {
            for (Iterator<Entry> it = buffer.descendingIterator(); it.hasNext() & maxEntries > 0; maxEntries--) {
                results.add(it.next());
            }
        }
        reverse(results);
        return results;
    }

    /**
     * Get slice of entries based on the id. If maxEntries is positive, returns entries newest than a given id,
     * otherwise oldest ones. The actual number if such records can be less than absolute value of maxEntries, so check
     * the returned List size.
     *
     * @param id         of the record to start with
     * @param maxEntries and the direction, positive means newest than id (entry.id > id), negative for older (entry.id
     *                   < id).
     *
     * @return possibly empty list of entries matching the criteria.
     */
    public List<Entry> slice(long id, int maxEntries) {
        List<Entry> copy = getCopy();
        Entry start = new Entry(id);
        int fromIndex = Collections.binarySearch(copy, start);
        if (maxEntries > 0)
            fromIndex++;
        if (fromIndex < 0)
            fromIndex = 0;
        int toIndex = fromIndex + maxEntries;
        if (toIndex < fromIndex) {
            int t = fromIndex;
            fromIndex = toIndex;
            toIndex = t;
        }
        int length = copy.size();
        if (fromIndex < 0)
            fromIndex = 0;
        if (toIndex >= length)
            toIndex = length;
        if (fromIndex >= length || toIndex < 1 || fromIndex == toIndex)
            return Collections.emptyList();
        return copy.subList(fromIndex, toIndex);
    }

    /**
     * clear the whole stored content.
     */
    public void clear() {
        buffer.clear();
    }

    /**
     * Get a copy af all currently stored entries
     *
     * @return list of entries sorted by id
     */
    public List<Entry> getCopy() {
        synchronized (buffer) {
            return new ArrayList<>(buffer);
        }
    }

}
