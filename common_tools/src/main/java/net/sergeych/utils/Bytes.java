/**
 *
 */
package net.sergeych.utils;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Wrap for byte[] utility class. Allow usage as an HashMap key. Very effective construction from
 * byte[] and back ({@link #toArray()} - no copying.
 *
 * @author sergeych
 */
public class Bytes implements Serializable {

    private static final Charset utf8 = Charset.forName("utf8");
    private static SecureRandom rng = null;
    //	static private Errlog.Printer log = new Errlog.Printer("Bytes");
    static private LogPrinter log = new LogPrinter("Bytes");
    final byte data[];
    private boolean noHashcode = true;
    private int cachedHashCode;

    public Bytes(String string) {
        this(string.getBytes(utf8));
    }

    public Bytes(final byte[]... arrays) {
        if (arrays.length == 1) {
            data = arrays[0];
        } else {
            int length = 0;
            for (byte[] x : arrays)
                length += x.length;
            data = new byte[length];
            int pos = 0;
            for (byte[] x : arrays) {
                System.arraycopy(x, 0, data, pos, x.length);
                pos += x.length;
            }
        }
    }

    /**
     * Construct by reading certain number of bytes, or all of them, from a stream. Please note that
     * if the stream contains less bytes than ordered, no exception will be thrown. This constructor
     * will provide empty buffer on empty stream.
     *
     * @param in
     *         stream to read from
     * @param length
     *         how many bytes to read. If &lt; 1, all file will be read.
     *
     * @throws IOException
     */
    public Bytes(InputStream in, int length) throws IOException {
        if (length > 0) {
            data = new byte[length];
            Ut.readFully(in, data);
        } else
            data = Ut.readFully(in);
    }

    /**
     * Construct from {@link ByteArrayOutputStream#toByteArray()} bytes
     *
     * @param bos
     */
    public Bytes(ByteArrayOutputStream bos) {
        this(bos.toByteArray());
    }

    /**
     * Create zero-filled instance of the specified size
     *
     * @param size
     *         desired size
     */
    public Bytes(int size) {
        this(new byte[size]);
    }

    /**
     * Construct from hexadecimal string. any spaces between digits are ignored.
     *
     * @param hex
     *         hexidecomal string, like "FF 01"
     *
     * @return
     */
    public static Bytes fromHex(String hex) {
        ArrayList<Byte> data = new ArrayList<>(hex.length() / 2);

        int l = hex.length();
        for (int i = 0; i < l; i++) {
            char c = hex.charAt(i);
            if (!Character.isWhitespace(c)) {
                if (i + 1 >= l)
                    throw new IllegalArgumentException("Hex format failure: even number of digits");
                int val = ((Character.digit(c, 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
                data.add((byte) val);
                i++;
            }
        }
        byte[] result = new byte[data.size()];
        int i = 0;
        for (byte b : data) {
            result[i++] = b;
        }
        return new Bytes(result);
    }

    /**
     * Convert HEX string (ignoring whitespaces) to a byte[]
     *
     * @param hex
     *         strign
     *
     * @return decoded data
     */
    public static byte[] hexToByteArray(String hex) {
        return Bytes.fromHex(hex).toArray();
    }


    /**
     * Decode Base64 string into Bytes instance
     *
     * @param baseStr
     *         base64 string, possibily split to lines. Tries to add missing '=' to the end
     *
     * @return
     */
    public static Bytes fromBase64(String baseStr) {
        baseStr = baseStr.replace("\n", "").replace("\t", "").replace(" ", "").replace("\r", "");
        int n = baseStr.length() % 4;
        if (n > 0) {
            StringBuilder sb = new StringBuilder(baseStr);
            while (n-- > 0)
                sb.append('=');
            return new Bytes(Base64.decodeLines(sb.toString()));
        } else
            return new Bytes(Base64.decodeLines(baseStr));
    }

    public static Bytes fromString(String str) {
        return new Bytes(str);
    }

    public static Bytes fromBigInt(BigInteger value) {
        return new Bytes(value.toByteArray());
    }

    /**
     * Create buffer as standard IEEE double(8) representation, little-endian.
     *
     * @param value
     *
     * @return
     */
    static public Bytes fromDouble(double value) {
        byte[] bytes = new byte[8];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putDouble(value);
        return new Bytes(bytes);
    }

    public int size() {
        return data.length;
    }

    /**
     * Check whether that the buffer is empty
     *
     * @return true if there is no one byte in the buffer
     */
    public boolean empty() {
        return data == null || data.length == 0;
    }

    /**
     * Reverse order of own bytes. Chages own data and return self.
     *
     * @return self, after reversing all bytes.
     */
    public Bytes flipSelf() {
        int i = 0, j = data.length - 1;
        while (i < j) {
            byte x = data[i];
            data[i++] = data[j];
            data[j--] = x;
        }
        if (data.length > 0)
            noHashcode = true;
        return this;
    }

    /**
     * return underlying bytes. This is cheap operation: no copying/conversion is performed.
     *
     * @return the wrapped bytes
     */
    public final byte[] toArray() {
        return data;
    }

    /**
     * Writes dump to standard output, in the same format as {@link #toDump(byte[])}
     */
    public void dump() {
        Bytes.dump(data);
    }

    /**
     * Writes dump into standrd output. Same as {@link #toDump(byte[])}.
     *
     * @param data
     *         data to dump.
     */
    static public void dump(byte data[]) {
        String[] lines = Bytes.toDump(data);
        if (lines.length > 0) {
            for (String s : lines)
                log.i(s);
        } else
            log.i("Dump: no data");
    }

    /**
     * Generate dump string with address, hex bytes and ASCII character areas, line endings are
     * "\n"s.
     *
     * @param data
     *         data to dump
     *
     * @return
     */
    static public String[] toDump(byte[] data) {
        ArrayList<String> lines = new ArrayList<String>();
        StringBuilder line = null;
        if (data.length != 0) {
            for (int i = 0; i < data.length; i++) {
                if (i % 16 == 0) {
                    if (line != null) {
                        line.append(dumpChars(data, i - 16));
                        lines.add(line.toString());
                    }
                    line = new StringBuilder(String.format("%04X ", i));
                }
                line.append(String.format("%02X ", data[i]));
            }
            if (line != null) {
                int l = data.length;
                int fill = 16 - (l % 16);
                if (fill < 16)
                    while (fill-- > 0)
                        line.append("   ");
                int index = l - (l % 16);
                line.append(dumpChars(data, index < l ? index : l - 16));
                lines.add(line.toString());
            }
        }
        return lines.toArray(new String[0]);
    }

    private static String dumpChars(byte[] data, int from) {
        StringBuilder b = new StringBuilder(22);
        b.append("|");
        int max = Math.min(data.length, from + 16);
        while (from < max) {
            int ch = data[from++];
            if (ch >= ' ' && ch < 127)
                b.append((char) ch);
            else
                b.append('.');
        }
        int f16 = from % 16;
        if (f16 > 0) {
            int cnt = 16 - f16;
            while (cnt-- > 0)
                b.append(' ');
        }
        return b.append("|").toString();
    }

    public String toDump() {
        StringBuilder b = new StringBuilder();
        for (String line : Bytes.toDump(data)) {
            b.append(line);
            b.append("\n");
        }
        return b.toString();
    }

    /**
     * Convert to a BigInteger value. Be sure to check desired bytes order and call {@link
     * #flipSelf()} first if necessary. See {@link BigInteger#BigInteger(byte[])}.
     *
     * @return resulting BigInteger instance
     */
    public BigInteger toBigInteger() {
        return new BigInteger(data);
    }

    /**
     * Tries to convert bytes to UTF8 string. If the string seems to contain non-characters. convert
     * it to base64 for readability. the type is shown by prefix, "t:" for utf8 text and "b64:" for
     * base64
     */
    public String inspect() {
        String res = new String(data, utf8);
        for (int i = 0; i < res.length(); i++) {
            int codePoint = res.codePointAt(i);
            if (!Character.isDefined(codePoint))
                return "b64:" + toBase64();
        }
        return "t:" + res;
    }

    public String toBase64() {
        return Base64.encodeString(data);
    }

    public String toHex() {
        StringBuilder str = new StringBuilder();
        for (byte b : data)
            str.append(String.format("%02X ", b));
        return str.toString().trim();
    }

    public String toHex(boolean useSpaces) {
        StringBuilder str = new StringBuilder();
        if (useSpaces) {
            for (byte b : data)
                str.append(String.format("%02X ", b));
        } else {
            for (byte b : data)
                str.append(String.format("%02X", b));
        }
        return str.toString().trim();
    }


    public String toBase64Lines() {
        return Base64.encodeLines(data);
    }

    @Override
    public int hashCode() {
        if (noHashcode) {
            CRC32 crc = new CRC32();
            crc.update(data);
            cachedHashCode = (int) crc.getValue();
            noHashcode = false;
        }
        return cachedHashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Bytes) {
            byte[] other = ((Bytes) obj).data;
            return Arrays.equals(other, data);
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return new String(data, utf8);
    }

    public void write(OutputStream out) throws IOException {
        out.write(data);
    }

    public Object toDouble() {
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getDouble();
    }

    public Bytes sha1() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 is not implemented");
        }
        md.update(data);
        return new Bytes(md.digest());
    }

    /**
     * Calculate SHA256 hash for this instance optionally with any number of additional chinks. Hash
     * is calculated on own bytes then on chunks if any.
     *
     * @param chunks
     *         chunks to concatenate to the calculated hash. Each chink should be either Bytes
     *         instance, String or byte[] array
     *
     * @return sha256 hashcode (32 bytes) in wrapped into the Bytes instance.
     */
    public Bytes sha256(Object... chunks) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 is not implemented");
        }
        md.update(data);
        for (Object x : chunks) {
            if (x instanceof String)
                x = ((String) x).getBytes();
            if (x instanceof byte[])
                md.update((byte[]) x);
            else if (x instanceof Bytes)
                md.update(((Bytes) x).data);
        }
        return new Bytes(md.digest());
    }

    /**
     * Get an input stream to read contents of this buffer
     *
     * @return input stram
     */
    public ByteArrayInputStream toInputStream() {
        return new ByteArrayInputStream(data);
    }

    /**
     * Extract part of the data from ith index (inclusive) to the end. Is a shortcat for
     * <code>part(i, 0);</code> see {@link #part(int, int)} for details. Note this method actually
     * copy bytes!
     *
     * @param start
     *         index to copy. If &lt; 0 means offstet from the end.
     *
     * @return {@link Bytes} instance with copied bytes.
     */
    public Bytes part(int start) {
        return part(start, 0);
    }

    /**
     * Extract a part of bytes into new instance. Bytes are copied!. This utility function
     * recalculates its params for convience:
     * <p>
     * <p>
     * Negative start index means offser from the last element:
     * <p>
     * <pre>
     * part(-3, 3); // extracts last 3 bytes
     * </pre>
     * <p>
     * Length == 0 means all bytes since the start:
     * <p>
     * <pre>
     * part(10, 0); // all bytes starting with 10th
     * part(-3, 0); // extracts last 3 bytes
     * </pre>
     * Length parameter is truncated if is bigger than remaining bytes length.
     *
     * @param start
     *         the start index, inclusive. If &lt; 0 then means offset from the end of data
     * @param length
     *         the number of bytes to copy. When 0 then the all the rest of bytes will be copied. Is
     *         automatically truncated as need.
     *
     * @return the {@link Bytes} instance with copied part.
     */
    public Bytes part(int start, int length) {
        if (start < 0)
            start = data.length + start;
        if (start < 0)
            throw new IndexOutOfBoundsException("Bytes#part: start index out of bounds");
        if (length < 1)
            length = data.length;
        int end = start + length;
        if (end > data.length)
            end = data.length;
        byte[] dst = Arrays.copyOfRange(data, start, end);
        return new Bytes(dst);
    }

    /**
     * Return new block that is contcatenation of this block and length random bytes
     *
     * @param length
     *
     * @return
     */
    public Bytes fillRandom(int length) {
        return this.concatenate(Bytes.random(length));
    }

    /**
     * Return new buffer that is a concatenation of this and other.
     *
     * @param other
     *         Bytes buffer to concatenate
     *
     * @return new Bytes instance
     */
    public Bytes concatenate(Bytes other) {
        int newLength = data.length + other.data.length;
        byte res[] = Arrays.copyOf(data, newLength);
        int j = 0;
        for (int i = data.length; i < newLength; i++)
            res[i] = other.data[j++];
        return new Bytes(res);
    }

    public <T> Bytes concatenate(List<T> other) {
        int newLength = data.length + other.size();
        byte res[] = Arrays.copyOf(data, newLength);
        int j = 0;
        for (int i = data.length; i < newLength; i++) {
            res[i] = (byte) ((Integer) other.get(j++) & 0xFF);
        }
        return new Bytes(res);
    }

    /**
     * Return new buffer that is a concatenation of this and other.
     *
     * @param otherBytes
     *         Bytes buffer to concatenate
     *
     * @return new Bytes instance
     */
    public Bytes concatenate(byte[] otherBytes) {
        return concatenate(new Bytes(otherBytes));
    }

    /**
     * Create random bytes using {@link SecureRandom} RNG in SHA1PRNG mode.
     *
     * @param length
     *         length if bytes
     *
     * @return
     */
    public static Bytes random(int length) {
        if (rng == null) {
            try {
                rng = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                log.e("Cant create RNG for ids!");
                throw new RuntimeException("Can't create suitable PRNG");
            }
        }
        byte[] data = new byte[length];
        rng.nextBytes(data);
        return new Bytes(data);
    }

    /**
     * Return new Bytes instance padded to the required size. If the current size is less than or
     * equal to the size, returns the copy of this.
     *
     * @param size
     *         desired size
     * @param fillByte
     *         fill byte
     *
     * @return new {@link Bytes} instance padded if necessary. Its size is always lesss or equal
     * than size.
     */
    public Bytes padToSize(int size, int fillByte) {
        if (data.length >= size)
            return new Bytes(data);
        byte[] res = Arrays.copyOf(data, size);
        if (fillByte != 0) {
            for (int i = data.length; i < size; i++)
                res[i] = (byte) fillByte;
        }
        return new Bytes(res);
    }

    public Bytes concatenate(String s) {
        return concatenate(new Bytes(s));
    }

    /**
     * Create a copy with this data in reverse order
     *
     * @return a new copy of Bytes in reverse order
     */
    public Bytes reverse() {
        int length = data.length;
        int l1 = length - 1;
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++)
            result[i] = data[l1 - i];
        return new Bytes(result);
    }

    public Bytes correctIntFromGMP() {
//        byte[] zero = new byte[] { 0 };
//        return new Bytes(zero, reverse().toArray());
        return this;
    }

//    public String toNameCode32() {
//        return NameCode32.encode(data);
//    }

}
