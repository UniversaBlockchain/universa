/*
 * [Base64.java]
 *
 * Summary: Encode arbitrary binary into printable ASCII using BASE64 encoding.
 *
 * Copyright: (c) 1999-2014 Roedy Green, Canadian Mind Products, http://mindprod.com
 *
 * Licence: This software may be copied and used freely for any purpose but military.
 *          http://mindprod.com/contact/nonmil.html
 *
 * Requires: JDK 1.7+
 *
 * Created with: JetBrains IntelliJ IDEA IDE http://www.jetbrains.com/idea/
 *
 * Version History:
 *  1.0 1999-12-03 posted in comp.lang.java.programmer.
 *  1.1 1999-12-04 more symmetrical encoding algorithm. more accurate StringBuffer allocation size.
 *  1.2 2000-09-09 now handles decode as well.
 *  1.3 2000-09-12 fix problems with estimating output length in encode
 *  1.4 2002-02-15 correct bugs with uneven line lengths, allow you to configure line
 *                 separator. now need Base64 object and instance methods. new mailing address.
 *  1.5 2006-01-01
 *  1.6 2007-01-01
 *  1.7 2007-03-15 add Example
 *  1.8 2007-03-15 tidy.
 *  1.9 2007-05-20 add icon and pad
 */
package net.sergeych.utils;

import static java.lang.System.out;

/**
 * Encode arbitrary binary into printable ASCII using BASE64 encoding.
 * <p>
 * Base64 is a way of encoding 8-bit characters using only ASCII printable characters similar to UUENCODE. UUENCODE
 * includes a filename where BASE64 does not. The spec is described in RFC 2045. Base64 is a scheme where 3 bytes are
 * concatenated, then split to form 4 groups of 6-bits each; and each 6-bits gets translated to an encoded printable
 * ASCII character, via a table lookup. An encoded string is therefore longer than the original by about 1/3. The
 * &quot;=&quot; character is used to pad the end. Base64 is used, among other things, to encode the user:password
 * string in an Authorization: header for HTTP. Don't confuse Base64 with x-www-form-urlencoded which is handled by
 * Java.net.URLEncoder.encode/decode
 * <p>
 * If you don't like this code, there is another implementation at http://www.ruffboy .com/download.htm Sun has an
 * undocumented method called sun.misc.Base64Encoder.encode. You could use hex, simpler to code, but not as compact.
 * <p>
 * If you wanted to encode a giant file, you could do it in large chunks that are even multiples of 3 bytes, except for
 * the last chunk, and append the outputs.
 * <p>
 * To encode a string, rather than binary data java.net.URLEncoder may be better. See printable characters in the Java
 * glossary for a discussion of the differences.
 * <p>
 * Base 64 armouring uses only the characters A-Z \\p{Lower} 0-9 +/=. This makes it suitable for encoding binary data as
 * SQL strings, that will work no matter what the encoding. Unfortunately + / and = all have special meaning in URLs.
 * <pre>
 * Works exactly like Base64 except avoids using the characters
 * + / and =.  This means Base64u-encoded data can be used either
 * URLCoded or plain in
 * URL-Encoded contexts such as GET, PUT or URLs. You can treat the
 * output either as
 * not needing encoding or already URLEncoded.
 *
 * </pre>
 * <p>
 * Base64 ASCII armouring. Encode arbitrary binary into printable ASCII using BASE64 encoding. very loosely based on the
 * Base64 Reader by: Dr. Mark Thornton<br> Optrak Distribution Software Ltd. http://www.optrak.co.uk and Kevin Kelley's
 * http://www.ruralnet.net/~kelley/java/Base64.java<br>
 * <p>
 * real.sergeych@gmail.com: Added some utility functions to simplify its usage.
 *
 * @author Sergey Chernov, iCodici S.n.C
 * @author Roedy Green, Canadian Mind Products
 * @since 1999-12-03
 */
public class Base64 {

    /**
     * Decodes a byte array from Base64 format and ignores line separators, tabs and blanks. CR, LF, Tab and Space
     * characters are ignored in the input data.
     *
     * @param s A Base64 String to be decoded.
     * @return An array containing the decoded data bytes.
     * @throws IllegalArgumentException If the input is not valid Base64 encoded data.
     */
    public static byte[] decodeLines(String s) {
        char[] buf = new char[s.length()];
        int p = 0;
        for (int ip = 0; ip < s.length(); ip++) {
            char c = s.charAt(ip);
            if (c != ' ' && c != '\r' && c != '\n' && c != '\t')
                buf[p++] = c;
        }
        return new Base64().decode(new String(buf));
    }

    public static String encodeString(byte[] data) {
        Base64 b = new Base64();
        b.setLineLength(0);
        return b.encode(data);
    }

    public static String encodeCompactString(byte[] data) {
        String s = encodeString(data);
        int last = s.length() - 1;
        while (last > 0 && s.charAt(last) == '=') last--;
        return s.substring(0, last + 1);
    }

    public static String encodeLines(byte[] data) {
        Base64 b = new Base64();
        b.setLineLength(72);
        return b.encode(data);
    }


    /**
     * used to disable test driver.
     */
    protected static final boolean DEBUGGING = false;

    /**
     * Marker value for chars we just ignore, e.g. \n \r high ascii.
     */
    protected static final int IGNORE = -1;

    /**
     * Marker for = trailing pad.
     */
    protected static final int PAD = -2;

    private static final int FIRST_COPYRIGHT_YEAR = 1999;

    /**
     * undisplayed copyright notice
     */
    private static final String EMBEDDED_COPYRIGHT =
            "Copyright: (c) 1999-2014 Roedy Green, Canadian Mind Products, http://mindprod.com";

    /**
     * when package was released.
     */
    private static final String RELEASE_DATE = "2007-05-20";

    /**
     * name of package.
     */
    private static final String TITLE_STRING = "Base64";

    /**
     * version of package.
     */
    private static final String VERSION_STRING = "1.9";

    /**
     * binary value encoded by a given letter of the alphabet 0..63.
     */
    protected static int[] cv;

    /**
     * letter of the alphabet used to encode binary values 0..63
     */
    protected static char[] vc;

    /**
     * how we separate lines, e.g. \n, \r\n, \r etc.
     */
    protected String lineSeparator = System.getProperty("line.separator");

    /**
     * letter of the alphabet used to encode binary values 0..63, overridden in Base64u.
     */
    protected char[] valueToChar;

    /**
     * special character 1, will be - in Base64u.
     */
    protected char spec1 = '+';

    /**
     * special character 2, will be in in Base64u.
     */
    protected char spec2 = '/';

    /**
     * special character 3, will be * in Base64u.
     */
    protected char spec3 = '=';

    /**
     * binary value encoded by a given letter of the alphabet 0..63, overridden in Base64u.
     */
    protected int[] charToValue;

    /**
     * max chars per line, excluding lineSeparator. A multiple of 4.
     */
    protected int lineLength = 72;

    /**
     * constructor.
     */
    public Base64() {
        spec1 = '+';
        spec2 = '/';
        spec3 = '=';
        initTables();
    }

    /**
     * debug display array as hex.
     *
     * @param b byte array to display.
     */
    public static void show(byte[] b) {
        for (final byte aB : b) {
            out.print(Integer.toHexString(aB & 0xff) + " ");
        }
        out.println();
    }

    /**
     * test driver.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        if (DEBUGGING) {
            byte[] a = {(byte) 0xfc, (byte) 0x0f, (byte) 0xc0};
            byte[] b = {(byte) 0x03, (byte) 0xf0, (byte) 0x3f};
            byte[] c = {(byte) 0x00, (byte) 0x00, (byte) 0x00};
            byte[] d = {(byte) 0xff, (byte) 0xff, (byte) 0xff};
            byte[] e = {(byte) 0xfc, (byte) 0x0f, (byte) 0xc0, (byte) 1};
            byte[] f =
                    {(byte) 0xfc, (byte) 0x0f, (byte) 0xc0, (byte) 1, (byte) 2};
            byte[] g = {
                    (byte) 0xfc,
                    (byte) 0x0f,
                    (byte) 0xc0,
                    (byte) 1,
                    (byte) 2,
                    (byte) 3};
            byte[] h = "AAAAAAAAAAB".getBytes();
            show(a);
            show(b);
            show(c);
            show(d);
            show(e);
            show(f);
            show(g);
            show(h);
            Base64 b64 = new Base64();
            show(b64.decode(b64.encode(a)));
            show(b64.decode(b64.encode(b)));
            show(b64.decode(b64.encode(c)));
            show(b64.decode(b64.encode(d)));
            show(b64.decode(b64.encode(e)));
            show(b64.decode(b64.encode(f)));
            show(b64.decode(b64.encode(g)));
            show(b64.decode(b64.encode(h)));
            b64.setLineLength(8);
            show((b64.encode(h)).getBytes());
        }
    } // end gui.main.main

    /**
     * decode a well-formed complete Base64 string back into an array of bytes. It must have an even multiple of 4 data
     * characters (not counting \n), padded out with = as needed.
     *
     * @param s base64-encoded string
     * @return plaintext as a byte array.
     */
    @SuppressWarnings("fallthrough")
    public byte[] decode(String s) {
        // estimate worst case size of output array, no embedded newlines.
        byte[] b = new byte[(s.length() / 4) * 3];
        // tracks where we are in a cycle of 4 input chars.
        int cycle = 0;
        // where we combine 4 groups of 6 bits and take apart as 3 groups of 8.
        int combined = 0;
        // how many bytes we have prepared.
        int j = 0;
        // will be an even multiple of 4 chars, plus some embedded \n
        int len = s.length();
        int dummies = 0;
        for (int i = 0; i < len; i++) {
            int c = s.charAt(i);
            int value = (c <= 255) ? charToValue[c] : IGNORE;
            // there are two magic values PAD (=) and IGNORE.
            switch (value) {
                case IGNORE:
                    // e.g. \n, just ignore it.
                    break;
                case PAD:
                    value = 0;
                    dummies++;
                    // deliberate fallthrough
                default:
                    /* regular value character */
                    switch (cycle) {
                        case 0:
                            combined = value;
                            cycle = 1;
                            break;
                        case 1:
                            combined <<= 6;
                            combined |= value;
                            cycle = 2;
                            break;
                        case 2:
                            combined <<= 6;
                            combined |= value;
                            cycle = 3;
                            break;
                        case 3:
                            combined <<= 6;
                            combined |= value;
                            // we have just completed a cycle of 4 chars.
                            // the four 6-bit values are in combined in
                            // big-endian order
                            // peel them off 8 bits at a time working lsb to msb
                            // to get our original 3 8-bit bytes back
                            b[j + 2] = (byte) combined;
                            combined >>>= 8;
                            b[j + 1] = (byte) combined;
                            combined >>>= 8;
                            b[j] = (byte) combined;
                            j += 3;
                            cycle = 0;
                            break;
                    }
                    break;
            }
        } // end for
        if (cycle != 0) {
            throw new ArrayIndexOutOfBoundsException(
                    "Input to decode not an even multiple of 4 characters; pad with "
                            + spec3
            );
        }
        j -= dummies;
        if (b.length != j) {
            byte[] b2 = new byte[j];
            System.arraycopy(b, 0, b2, 0, j);
            b = b2;
        }
        return b;
    } // end decode

    /**
     * Encode an arbitrary array of bytes as Base64 printable ASCII. It will be broken into lines of 72 chars each. The
     * last line is not terminated with a line separator. The output will always have an even multiple of data
     * characters, exclusive of \n. It is padded out with =.
     *
     * @param b byte array to encode, typically produced by a ByteArrayOutputStream.
     * @return base-64 encoded String, not char[] or byte[].
     */
    public String encode(byte[] b) {
        // Each group or partial group of 3 bytes becomes four chars
        // covered quotient
        int outputLength = ((b.length + 2) / 3) * 4;
        // account for trailing newlines, on all but the very last line
        if (lineLength != 0) {
            int lines = (outputLength + lineLength - 1) / lineLength - 1;
            if (lines > 0) {
                outputLength += lines * lineSeparator.length();
            }
        }
        // must be local for recursion to work.
        StringBuilder sb = new StringBuilder(outputLength);
        // must be local for recursion to work.
        int linePos = 0;
        // first deal with even multiples of 3 bytes.
        int len = (b.length / 3) * 3;
        int leftover = b.length - len;
        for (int i = 0; i < len; i += 3) {
            // Start a new line if next 4 chars won't fit on the current line
            // We can't encapsulete the following code since the variable need
            // to
            // be local to this incarnation of encode.
            linePos += 4;
            if (linePos > lineLength) {
                if (lineLength != 0) {
                    sb.append(lineSeparator);
                }
                linePos = 4;
            }
            // get next three bytes in unsigned form lined up,
            // in big-endian order
            int combined = b[i] & 0xff;
            combined <<= 8;
            combined |= b[i + 1] & 0xff;
            combined <<= 8;
            combined |= b[i + 2] & 0xff;
            // break those 24 bits into a 4 groups of 6 bits,
            // working LSB to MSB.
            int c3 = combined & 0x3f;
            combined >>>= 6;
            int c2 = combined & 0x3f;
            combined >>>= 6;
            int c1 = combined & 0x3f;
            combined >>>= 6;
            int c0 = combined & 0x3f;
            // Translate into the equivalent alpha character
            // emitting them in big-endian order.
            sb.append(valueToChar[c0]);
            sb.append(valueToChar[c1]);
            sb.append(valueToChar[c2]);
            sb.append(valueToChar[c3]);
        }
        // deal with leftover bytes
        switch (leftover) {
            case 0:
            default:
                // nothing to do
                break;
            case 1:
                // One leftover byte generates xx==
                // Start a new line if next 4 chars won't fit on the current
                // line
                linePos += 4;
                if (linePos > lineLength) {
                    if (lineLength != 0) {
                        sb.append(lineSeparator);
                    }
                    // linePos = 4;
                }
                // Handle this recursively with a faked complete triple.
                // Throw away last two chars and replace with ==
                sb.append(encode(new byte[]{b[len], 0, 0}).substring(0,
                                                                     2));
                sb.append(spec3);
                sb.append(spec3);
                break;
            case 2:
                // Two leftover bytes generates xxx=
                // Start a new line if next 4 chars won't fit on the current
                // line
                linePos += 4;
                if (linePos > lineLength) {
                    if (lineLength != 0) {
                        sb.append(lineSeparator);
                    }
                    // linePos = 4;
                }
                // Handle this recursively with a faked complete triple.
                // Throw away last char and replace with =
                sb.append(encode(new byte[]{
                        b[len], b[len + 1], 0}).substring(0, 3));
                sb.append(spec3);
                break;
        } // end switch;
        if (outputLength != sb.length()) {
            out.println(
                    "oops: minor program flaw: output length mis-estimated");
            out.println("estimate:" + outputLength);
            out.println("actual:" + sb.length());
        }
        return sb.toString();
    } // end encode

    /**
     * determines how long the lines are that are generated by encode. Ignored by decode.
     *
     * @param length 0 means no newlines inserted. Must be a multiple of 4.
     */
    public final void setLineLength(int length) {
        this.lineLength = (length / 4) * 4;
    }

    /**
     * How lines are separated. Ignored by decode.
     *
     * @param lineSeparator may be "" but not null. Usually contains only a combination of chars \n and \r. Could be any
     *                      chars not in set A-Z \\p{Lower} 0-9 + /.
     */
    public final void setLineSeparator(String lineSeparator) {
        this.lineSeparator = lineSeparator;
    }

    /**
     * Initialise both static and instance table.
     */
    private void initTables() {
        /* initialise valueToChar and charToValue tables */
        if (vc == null) {
            // statics are not initialised yet
            vc = new char[64];
            cv = new int[256];
            // build translate valueToChar table only once.
            // 0..25 -> 'A'..'Z'
            for (int i = 0; i <= 25; i++) {
                vc[i] = (char) ('A' + i);
            }
            // 26..51 -> 'a'..'z'
            for (int i = 0; i <= 25; i++) {
                vc[i + 26] = (char) ('a' + i);
            }
            // 52..61 -> '0'..'9'
            for (int i = 0; i <= 9; i++) {
                vc[i + 52] = (char) ('0' + i);
            }
            vc[62] = spec1;
            vc[63] = spec2;
            // build translate charToValue table only once.
            for (int i = 0; i < 256; i++) {
                cv[i] = IGNORE;// default is to ignore
            }
            for (int i = 0; i < 64; i++) {
                cv[vc[i]] = i;
            }
            cv[spec3] = PAD;
        }
        valueToChar = vc;
        charToValue = cv;
    }

    public static byte[] decodeCompactString(String s) {
        StringBuilder sb = new StringBuilder(s.trim());
        int n = sb.length() % 4;
        while (n-- > 0)
            sb.append('=');
        return Base64.decodeLines(sb.toString());
    }
} // end Base64
