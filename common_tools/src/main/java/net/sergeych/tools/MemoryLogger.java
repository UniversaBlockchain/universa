package net.sergeych.tools;


import java.io.PrintStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Utility class to keep (rotate) last logged strings. As for now does nothing but keep it and manage the size.
 * Threadsafe.
 */
public class MemoryLogger {

    static DateTimeFormatter fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault());

    public static class Entry {
        public final Instant instant = Instant.now();
        public final String message;

        public Entry(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return "" + fmt.format(instant) + " " + message;
        }
    }

    private int maxLines;
    private final ConcurrentLinkedQueue<Entry> buffer = new ConcurrentLinkedQueue<>();
    private PrintStream pstream = null;

    public MemoryLogger(int maxLines) {
        this.maxLines = maxLines;
    }

    public void log(String message) {
        Entry entry = new Entry(message);
        buffer.add(entry);
        synchronized (this) {
            while (buffer.size() > maxLines)
                buffer.poll();
        }
    }

    public void clear() {
        buffer.clear();
    }

    public List<Entry> getCopy() {
        return new ArrayList<>(buffer);
    }
}
