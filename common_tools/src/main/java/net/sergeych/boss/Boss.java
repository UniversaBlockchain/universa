/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

/**
 *
 */
package net.sergeych.boss;

import net.sergeych.biserializer.BiAdapter;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * BOSS (Binary Object Serialization Specification) protocol version 1.4 final stream mode specification (no-cache).
 * <p>
 * Specification version: 1.4 (stream mode with no caching, regular cached mode)
 * <p>
 * BOSS is an acronym for Binary Object Streaming Specification.
 * <p>
 * The bit-effective, platform-independent streamable and traversable typed binary protocol. Allow to effectively store
 * small or any sized integers, strings or binary data of any size, floats and doubles, arrays and hashes and time
 * objects in a very effective way. It caches repeating objects and stores/restores links to objects.
 * <p>
 * The protocol allow to effectively store texts and binary data of absolutely any size, signed integers of absolutely
 * any size, arrays and hashes with no limit on items and overall gross size. It is desirable to use build-in
 * compression when appropriate.
 * <p>
 * Streamable means that you can use a pipe (for example tcp/ip), put the object at one side and load it on other,
 * one-by-one, and caching and links will be restored properly.
 * <p>
 * Initially, this protocol was intended to be used in secure communications. Its gui.main.main goal was to very
 * effective data sending and is a great replacement for json/boss/whatever. For example, typical JSON reduces in size
 * twice with Boss.
 * <p>
 * Boss protocol also allow to transparently compress its representations.
 * <p>
 * Boss also supports "stream mode" that lacks tree reconstruction but could be effectively use when implementing
 * long-living streams (e.g. stream protocols). In regular mode it causes unlimited cache grows as Boss would try to
 * reconstruct all possible references to already serialized objects. In the stream mode only strings are cached, and
 * cache size and capacity are dynamically limited. Boss writes stream mode marker and handles stream mode on receiving
 * end automatically.
 * <p>
 * Supported types:
 * <p>
 * <pre>
 *  - Signed integers of any length
 *  - Signed floats and doubles (4 or 8 bytes)
 *  - Boolean values (true/false)
 *  - UTF-8 encoded texts, any length
 *  - Binary data, any length
 *  - Date objects (date and time with 1 second resolution)
 *  - Arrays with any number of mixed type elements
 *  - Hashes with any keys and values and unlimited length
 *  - Reference to the object that already was serialized (unless in stream mode)
 * </pre>
 *
 * @author sergeych
 */
@SuppressWarnings("unused")
public class Boss {

    /**
     * @return true if Boss decodes date to  {@link Date} class instead of {@link ZonedDateTime} (default behavior)
     */
    public static boolean isUseOldDates() {
        return useOldDates;
    }

    /**
     * Set to true to use {@link Date} class instead of {@link ZonedDateTime} (default)
     */
    public static void setUseOldDates(boolean useOldDates) {
        Boss.useOldDates = useOldDates;
    }

    static private boolean useOldDates = false;


    static private final int TYPE_INT = 0;
    static private final int TYPE_EXTRA = 1;
    static private final int TYPE_NINT = 2;
    static private final int TYPE_TEXT = 3;
    static private final int TYPE_BIN = 4;
    static private final int TYPE_CREF = 5;
    static private final int TYPE_LIST = 6;
    static private final int TYPE_DICT = 7;
    static private final int XT_DZERO = 1; // double 0.0
    static private final int XT_DONE = 2; // double 1.0
    static private final int XT_DMINUSONE = 4; // double -1.0
    // TFLOAT = 6; // 32-bit IEEE float
    static private final int XT_DOUBLE = 7; // 64-bit IEEE float
    static private final int XT_TTRUE = 12;
    static private final int XT_FALSE = 13;
    // static private final int TCOMPRESSED = 14;
    static private final int XT_TIME = 15;
    static private final int XT_STREAM_MODE = 16;

    // static private final int TOBJECT = 8; // object record
    // TMETHOD = 9; // instance method
    // TFUNCTION = 10; // callable function
    // TGLOBREF = 11; // global reference

    /**
     * Encodes one or more objects one by one. It will need corresponding number of read calls
     *
     * @param objects objects one by one
     *
     * @return binary data
     */
    public static Bytes dump(Object first, Object... objects) {
        return new Bytes(dumpToArray(first, objects));
    }

    public static byte[] pack(Object object) {
        return dumpToArray(object);
    }

    /**
     * Encodes one or more objects one by one. It will need corresponding number of read calls
     *
     * @param objects objects one by one
     *
     * @return binary data as plain array
     */
    public static byte[] dumpToArray(Object first, Object... objects) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Writer w = new Writer(bos);
            w.writeObject(first);
            for (Object o : objects)
                w.writeObject(o);
            w.close();
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Boss can't dump this object", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> loadMap(Bytes bytes) {
        return (Map<K, V>) load(bytes);
    }

    /**
     * Load boss-encoded object tree from binary data. See {@link BiAdapter} on how to serialize any types.
     *
     * @param bytes data to load
     *
     * @return root object
     */
    public static <T> T load(Bytes bytes) {
        return load(bytes.toArray());
    }

    /**
     * Load boss-encoded object tree from binary data. See {@link BiAdapter} on how to serialize any types.
     *
     * @param data binary data to decode
     *
     * @return root object
     */
    static public <T> T load(byte[] data) {
        try {
            return (T) new Reader(new ByteArrayInputStream(data)).read();
        } catch (IOException e) {
            throw new IllegalArgumentException("Boss: can't parse data", e);
        }
    }

    static public <T> T load(byte[] data, BiDeserializer mapper) {
        try {
            return (T) new Reader(new ByteArrayInputStream(data), mapper).read();
        } catch (IOException e) {
            throw new IllegalArgumentException("Boss: can't parse data", e);
        }
    }

    /**
     * Load boss-encoded object and cast ti to {@link Binder}.
     *
     * @param data
     *
     * @return
     */
    public static Binder unpack(byte[] data) {
        return Boss.load(data);
    }

    public static void trace(byte[] packed) {
        Object obj = load(packed);
        System.out.println(traceObject("", obj));
    }

    public static void trace(Object object) {
        System.out.println(traceObject("", object));
    }

    private static String traceObject(String prefix, Object obj) {
        if (obj instanceof Bytes || obj instanceof byte[]) {
            Bytes bb = obj instanceof Bytes ? (Bytes) obj : new Bytes((byte[]) obj);
            if (bb.size() > 30)
                return prefix + bb.part(0, 30).toHex() + String.format(" ...(%d bytes)\n",
                                                                       bb.size()
                );
            return prefix + bb.toHex() + "\n";
        }
        if (obj instanceof Object[] || obj instanceof List<?>) {
            return traceArray(prefix, Do.collection(obj));
        }
        if (obj instanceof Binder) {
            Binder i = (Binder) obj;
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, Object> e : i.entrySet()) {
                b.append(prefix + e.getKey() + ":\n");
                b.append(traceObject(prefix + "  ", e.getValue()));
            }
            return b.toString();
        }
        return prefix + (obj == null ? "null" : "\"" + obj.toString() + "\"") + "\n";
    }

    private static String traceArray(String prefix, Collection<?> objects) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (Object x : objects) {
            String p1 = prefix + (i++) + ": ";
            b.append(traceObject(p1, x));
        }
        return b.toString();
    }

    static protected class Header {
        public int code;
        public long value;
        public BigInteger bigValue;

        public Header(int _code, long _value) {
            code = _code;
            value = _value;
        }

        public Header(int _code, BigInteger big) {
            bigValue = big;
            code = _code;
        }

        public Object smallestNumber(boolean negative) {
            if (bigValue != null)
                return negative ? bigValue.negate() : bigValue;
            if (Math.abs(value) <= 0x7FFFffff)
                return negative ? (int) -value : (int) value;
            return negative ? -value : value;
        }

        @Override
        public String toString() {
            return String.format("BH: code=%d value=%d bigValue=%s", code, value, bigValue);
        }
    }

    @SuppressWarnings("serial")
    static public class Dictionary extends Binder {
    }

    /**
     * BOSS serializer. Serialized object trees or, in stream mode, could be used to seralize a stream of objects.
     *
     * @author sergeych
     */
    static public class Writer {

        private OutputStream out;
        private HashMap<Object, Integer> cache;
        private boolean treeMode;
        private final BiSerializer biSerializer;

        /**
         * Creates writer to write to the output stream. Upon creation writer is alwais in tree mode.
         *
         * @param outputStream See {@link #setStreamMode()}
         */
        public Writer(OutputStream outputStream, BiSerializer biSerializer) {
            out = outputStream;
            cache = new HashMap<>();
            cache.put(null, 0);
            treeMode = true;
            this.biSerializer = biSerializer;
        }

        /**
         * Creates writer to write to the output stream. Upon creation writer is alwais in tree mode.
         *
         * @param outputStream See {@link #setStreamMode()}
         */
        public Writer(OutputStream outputStream) {
            this(outputStream, BossBiMapper.newSerializer());
        }

        public Writer() {
            this(new ByteArrayOutputStream());
        }

        static private int sizeInBytes(long value) {
            int cnt = 1;
            while (value > 255) {
                cnt++;
                value >>>= 8;
            }
            return cnt;
        }

        /**
         * Turn encoder to stream mode (e.g. no cache). In stram mode the protocol do not never cache nor remember
         * references, so restored object tree will not correspond to sources as all shared nodes will be copied. Stream
         * mode is used in large streams to avoid unlimited cache growths.
         * <p>
         * Stream more pushes the special record to the stream so the decoder {@link Reader} will know the more. Before
         * entering stream mode it is theoretically possible to write some cached trees, but this feature is yet
         * untested.
         *
         * @throws IOException
         */
        public void setStreamMode() throws IOException {
            cache = new HashMap<>();
            cache.put(null, 0);
            treeMode = false;
            writeHeader(TYPE_EXTRA, XT_STREAM_MODE);
        }

        /**
         * Serialize one or more objects. Objects will be serialized one by one, so corresponding number of read()'s is
         * necessary to retrieve them all.
         *
         * @param objects any number of Objects known to BOSS
         *
         * @return this Writer instance
         *
         * @throws IOException
         */
        public Writer write(Object... objects) throws IOException {
            for (Object x : objects)
                put(x);
            return this;
        }

        /**
         * Serialize single object known to boss (e.g. integers, strings, byte[] arrays or Bytes class instances, Date
         * instances, arrays, {@link ArrayList}, {@link HashMap}
         *
         * @param obj the root object to encode
         *
         * @return this instance to allow chaining calls
         *
         * @throws IOException
         */
        public Writer writeObject(Object obj) throws IOException {
            if (biSerializer != null && !(
                    obj instanceof Number || obj instanceof String || obj instanceof ZonedDateTime
                            || obj instanceof Boolean
            ))
                put(biSerializer.serialize(obj));
            else
                put(obj);
            return this;
        }

        private Writer put(Object obj) throws IOException {
            if (obj instanceof Number) {
                Number n = (Number) obj;
                if ((obj instanceof Integer) || (obj instanceof Long)) {
                    long value = n.longValue();
                    if (value >= 0)
                        writeHeader(TYPE_INT, value);
                    else
                        writeHeader(TYPE_NINT, -value);
                    return this;
                }
                if (obj instanceof BigInteger) {
                    BigInteger bi = (BigInteger) obj;
                    if (bi.signum() >= 0)
                        writeHeader(TYPE_INT, bi);
                    else
                        writeHeader(TYPE_NINT, bi.negate());
                    return this;
                }
                // Should be double
                double d = n.doubleValue();
                if (d == 0) {
                    writeHeader(TYPE_EXTRA, XT_DZERO);
                    return this;
                }
                if (d == -1.0) {
                    writeHeader(TYPE_EXTRA, XT_DMINUSONE);
                    return this;
                }
                if (d == 1.0) {
                    writeHeader(TYPE_EXTRA, XT_DONE);
                    return this;
                }
                writeHeader(TYPE_EXTRA, XT_DOUBLE);
                Bytes.fromDouble(n.doubleValue()).write(out);
                return this;
            }
            if (obj instanceof CharSequence) {
                String s = obj.toString();
                if (!tryWriteReference(s)) {
                    Bytes bb = new Bytes(s);
                    writeHeader(TYPE_TEXT, bb.size());
                    out.write(bb.toArray());
                }
                return this;
            }
            if (obj instanceof Bytes)
                obj = ((Bytes) obj).toArray();
            else if (obj instanceof ByteBuffer) {
                obj = ((ByteBuffer) obj).array();
            }
            if (obj instanceof byte[]) {
                byte[] bb = (byte[]) obj;
                if (!tryWriteReference(bb)) {
                    writeHeader(TYPE_BIN, bb.length);
                    out.write(bb);
                }
                return this;
            }
            if ((obj instanceof Object[])) {
                writeArray((Object[]) obj);
                return this;
            }
            if (obj instanceof Boolean) {
                writeHeader(TYPE_EXTRA, ((Boolean) obj) ? XT_TTRUE : XT_FALSE);
                return this;
            }
            if (obj instanceof Date) {
                writeHeader(TYPE_EXTRA, XT_TIME);
                writeEncoded(((Date) obj).getTime() / 1000);
                return this;
            }
            if (obj instanceof ZonedDateTime) {
                writeHeader(TYPE_EXTRA, XT_TIME);
                writeEncoded(((ZonedDateTime) obj).toEpochSecond());
                return this;
            }
            if (obj instanceof Map<?, ?>) {
                writeMap(obj);
                return this;
            }
            if ((obj instanceof Collection<?>)) {
                writeArray((Collection<?>) obj);
                return this;
            }
            if (obj == null) {
                // Null is CREF #0
                writeHeader(TYPE_CREF, 0);
                return this;
            }
            throw new IllegalArgumentException("unknown type: " + obj.getClass());
//            put(biSerializer.serialize(obj));
//            return this;
        }

        private void writeMap(Object obj) throws IOException {
            if (!tryWriteReference(obj)) {
                Map<?, ?> map = (Map<?, ?>) obj;
                writeHeader(TYPE_DICT, map.size());
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    put(e.getKey());
                    put(e.getValue());
                }
            }
        }

        private void writeArray(Object[] array) throws IOException {
            if (!tryWriteReference(array)) {
                writeHeader(TYPE_LIST, array.length);
                for (Object x : array)
                    put(x);
            }
        }

        private void writeArray(Collection<?> collection) throws IOException {
            if (!tryWriteReference(collection)) {
                writeHeader(TYPE_LIST, collection.size());
                for (Object x : collection)
                    put(x);
            }
        }

        private boolean tryWriteReference(Object obj) throws IOException {
            Integer index = cache.get(obj);
            if (index != null) {
                writeHeader(TYPE_CREF, index);
                return true;
            }
            // Cache put depends on the streamMode
            if (treeMode)
                cache.put(obj, cache.size());
            return false;
        }

        private void writeHeader(int code, BigInteger value) throws IOException {
            out.write(code | 0xF8);
            Bytes bb = Bytes.fromBigInt(value).flipSelf();
            writeEncoded(bb.size());
            bb.write(out);
        }

        private void writeHeader(int code, long value) throws IOException {
            assert code >= 0 && code <= 7;
            assert value >= 0;
            if (value < 23)
                out.write(code | ((int) value << 3));
            else {
                int n = sizeInBytes(value);
                if (n < 9) {
                    out.write(code | ((n + 22) << 3));
                } else {
                    out.write(code | 0xF8);
                    writeEncoded(n);
                }
                while (n-- > 0) {
                    out.write((int) value & 0xFF);
                    value >>>= 8;
                }
            }
        }

        private void writeEncoded(long value) throws IOException {
            while (value > 0x7f) {
                out.write(((int) value) & 0x7f);
                value >>= 7;
            }
            out.write(((int) value) | 0x80);
        }

        public void flush() throws IOException {
            out.flush();
        }

        public void close() throws IOException {
            out.close();
        }

        public OutputStream getOut() {
            return out;
        }

        /**
         * Return packed bytes. Works only if the underlying {@link OutputStream} was a {@link ByteArrayOutputStream}.
         * The default constructor {@link Writer#Writer()} does so.
         *
         * @return boss-packed data
         */
        public byte[] toByteArray() {
            if (out instanceof ByteArrayOutputStream)
                return ((ByteArrayOutputStream) out).toByteArray();
            throw new IllegalStateException("underlying OutputStream is not a ByteArrayOutputStream");
        }
    }

    // private static final Charset utf8 = Charset.forName("utf8");

    // private static void log(String s,Object... args) {
    // if( args.length > 0)
    // s = String.format(s, args);
    // System.out.println(s);
    // }

    static public class Reader {

        protected InputStream in;
        protected boolean treeMode;
        protected boolean showTrace = false;
        private ArrayList<Object> cache;
        private int maxCacheEntries, maxStringSize;
        private final BiDeserializer deserializer;

        public Reader(byte[] bytes) {
            this(new ByteArrayInputStream(bytes));
        }

        public Reader(InputStream stream, BiDeserializer deserializer) {
            in = stream;
            cache = new ArrayList<>();
            treeMode = true;
            this.deserializer = deserializer;
        }

        public Reader(InputStream stream) {
            this(stream, BossBiMapper.newDeserializer());
        }


        public void traceObject() throws IOException {
            Header h = readHeader();
            System.out.println(h);
        }

        private Header readHeader() throws IOException {
            int b = readByte();
            int code = b & 7;
            int value = b >>> 3;
            if (value >= 31) {
                int length = (int) readEncodedLong();
                return new Header(code, readBig(length));
            } else if (value > 22) {
                // up to 8 bytes, e.g. long
                return new Header(code, readLong(value - 22));
            }
            return new Header(code, value);
        }

        /**
         * Read byte or throw EOFException
         *
         * @return 0..255 byte value
         *
         * @throws IOException
         */
        private final int readByte() throws IOException {
            int i = in.read();
            if (i < 0)
                throw new EOFException();
            return i;
        }

        private long readEncodedLong() throws IOException {
            long value = 0;
            int shift = 0;
            while (true) {
                int n = readByte();
                value |= ((long) n & 0x7F) << shift;
                if ((n & 0x80) != 0)
                    return value;
                shift += 7;
            }
        }

        private BigInteger readBig(int length) throws IOException {
            Bytes bb = new Bytes(in, length);
            bb.flipSelf();
            return bb.toBigInteger();
        }

        private long readLong(int length) throws IOException {
            if (length <= 8) {
                long res = 0;
                int n = 0;
                while (length-- > 0) {
                    res |= (((long) readByte()) << n);
                    n += 8;
                }
                return res;
            } else
                throw new IllegalArgumentException("readlLong needs up to 8 bytes as length");
        }

        public void setTrace(boolean on) {
            showTrace = on;
        }

        protected void trace(String s) {
            if (showTrace)
                System.out.println(s);
        }

        private void traceCache() {
            trace("Cache: " + cache.toString());
        }

        /**
         * Read next object from the stream
         *
         * @param <T> expected object type
         *
         * @return next object casted to (T)
         *
         * @throws IOException
         */
        public <T> T read() throws IOException {
            Object x = get();
            if (deserializer == null || !(x instanceof Binder || x instanceof Collection))
                return (T) x;
            return deserializer.deserialize(x);
        }

        @SuppressWarnings("unchecked")
        private <T> T get() throws IOException {
            Header h = readHeader();
            trace("Header " + h);
            switch (h.code) {
                case TYPE_INT:
                    trace("Int: " + h.smallestNumber(false));
                    return (T) h.smallestNumber(false);
                case TYPE_NINT:
                    return (T) h.smallestNumber(true);
                case TYPE_BIN:
                case TYPE_TEXT: {
                    Bytes bb = new Bytes(in, (int) h.value);
                    if (h.code == TYPE_TEXT) {
                        String s = bb.toString();
                        cacheObject(s);
                        trace("t: " + s);
                        traceCache();
                        return (T) s;
                    }
                    cacheObject(bb);
                    return (T) bb;
                }
                case TYPE_LIST: {
                    ArrayList data = new ArrayList((int) (h.value < 0x10000 ? h.value : 4096));
                    cacheObject(data);
                    for (int i = 0; i < h.value; i++)
                        data.add(get());
                    return (T) data;
                }
                case TYPE_DICT: {
                    return readObject(h);
                }
                case TYPE_CREF:
                    int i = (int) h.value;
                    trace(String.format("Get from cache %d -> %s", h.value,
                                        i == 0 ? null : cache.get(i - 1)
                    ));
                    traceCache();
                    return i == 0 ? null : (T) cache.get(i - 1);
                case TYPE_EXTRA:
                    return (T) parseExtra((int) h.value);
            }
            throw new IOException("Bad BOSS header");
        }

        private <T> T readObject(Header h) throws IOException {
            Dictionary hash = new Dictionary();
            cacheObject(hash);
            for (int i = 0; i < h.value; i++)
                hash.put(get(), get());
//            if( hash.containsKey("__type") || hash.containsKey("__t"))
//                return (T) deserializer.deserialize(hash);
//            trace("Dict: " + hash);
            return (T) hash;
        }

        public int readInt() throws IOException {
            Number n = get();
            return n.intValue();
        }

        List<Object> getCache() {
            return cache;
        }

        private void cacheObject(Object obj) {
            if (treeMode)
                cache.add(obj);
            else {
                long len; // = 0
                if (obj instanceof String) {
                    len = ((String) obj).length();
                } else if (obj instanceof Bytes) {
                    len = ((Bytes) obj).size();
                } else if (obj instanceof byte[]) {
                    len = ((byte[]) obj).length;
                } else {
                    trace("Can't cache it in treemode! : " + obj);
                    return;
                }
                if (len <= maxStringSize) {
                    cache.add(obj);
                    if (cache.size() > maxCacheEntries)
                        cache.remove(0);
                }
            }
        }

        private Object parseExtra(int code) throws IOException {
            switch (code) {
                case XT_DZERO:
                    trace("extra 0");
                    return 0.0;
                case XT_DONE:
                    trace("extra 1");
                    return 1.0;
                case XT_DMINUSONE:
                    return -1.0;
                case XT_TTRUE:
                    return true;
                case XT_FALSE:
                    return false;
                case XT_TIME:
                    long seconds = readEncodedLong();
                    return useOldDates ? new Date(seconds * 1000) : Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault());
                case XT_STREAM_MODE:
                    setStreamMode();
                    return get();
                case XT_DOUBLE:
                    return new Bytes(in, 8).toDouble();
            }
            throw new IllegalArgumentException(String.format("Unknown extra code: %d", code));
        }

        private void setStreamMode() throws IOException {
            if (cache.size() > 0)
                cache = new ArrayList<>();
            treeMode = false;
        }

        /**
         * Read object and cast it to Bytes
         *
         * @return Bytes instance
         *
         * @throws IOException
         */
        public Bytes readBytes() throws IOException {
            return (Bytes) get();
        }

        /**
         * Convert next object ot byte[] (unless null that is null). Properly process byte[] array or Bytes instance.
         *
         * @return byte array or null in the case null reference was read.
         *
         * @throws IOException
         */
        public byte[] readBinary() throws IOException {
            Object x = get();
            if (x == null)
                return null;
            if (x.getClass().isArray())
                return (byte[]) x;
            return ((Bytes) x).toArray();
        }

        public Object[] readArray() throws IOException {
            return (Object[]) get();
        }

        /**
         * read object and cast it to BigInteger
         *
         * @throws IOException
         */
        public BigInteger readBigInteger() throws IOException {
            return (BigInteger) get();
        }

        public void close() throws IOException {
            in.close();
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> readMap() throws IOException {
            return (Map<String, Object>) get();
        }

        public long readLong() throws IOException {
            Number n = get();
            return n.longValue();
        }
    }
}


