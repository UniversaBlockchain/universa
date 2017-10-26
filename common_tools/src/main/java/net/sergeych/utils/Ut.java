package net.sergeych.utils;

import java.io.*;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Callable;

public class Ut {

    private static final String idChars =
            "0123456789_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static public Charset utf8 = Charset.forName("utf-8");
    private static SecureRandom rng;
    private static LogPrinter log = new LogPrinter("sergeych.ut");

    /**
     * Create strong enough pseudo-random alphanumeric String id. Uses [a-zA-Z0-9_] characters
     * only.
     *
     * @param length
     *         desired length
     *
     * @return
     */
    static public String randomString(int length) {
        if (rng == null) {
            try {
                rng = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                log.e("Cant create RNG for ids!");
                throw new RuntimeException("Can't create suitable PRNG");
            }
        }
        StringBuilder sb = new StringBuilder();
        while (length-- > 0) {
            sb.append(idChars.charAt(Math.abs(rng.nextInt()) % idChars.length()));
        }
        return sb.toString();
    }

    /**
     * Create strong enough pseudo-random alphanumeric String id. Uses [a-zA-Z0-9_] characters plus
     * any characters from extraChars
     *
     * @param length
     *         desired length
     * @param alphabet
     *         alphabet to select random characters from
     *
     * @return
     */
    static public String randomString(int length, String alphabet) {
        if (rng == null) {
            try {
                rng = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                log.e("Cant create RNG for ids!");
                throw new RuntimeException("Can't create suitable PRNG");
            }
        }
        StringBuilder sb = new StringBuilder();
        String chars = alphabet;
        while (length-- > 0) {
            sb.append(idChars.charAt(Math.abs(rng.nextInt()) % chars.length()));
        }
        return sb.toString();
    }

//    /**
//     * Read file into a string using UTF-8 encoding
//     *
//     * @param path
//     *         to file
//     *
//     * @return String
//     *
//     * @throws IOException
//     */
//    static public String readFile(String path) throws IOException {
//        return readFile(path, utf8);
//    }
//
//    /**
//     * Read contents of the file into a String using arbitrary encoding
//     *
//     * @param path
//     * @param encoding
//     *
//     * @return
//     *
//     * @throws IOException
//     */
//    static String readFile(String path, Charset encoding) throws IOException {
//        byte[] encoded = readFully(new FileInputStream(path));
//        return encoding.decode(ByteBuffer.wrap(encoded)).toString();
//    }

    /**
     * Java6 compatible way to read the whole stream into a bytes array
     *
     * @param inputStream
     *
     * @return
     *
     * @throws IOException
     */
    static public byte[] readFully(InputStream inputStream) throws IOException {
        if (inputStream == null)
            throw new IllegalArgumentException("Stream must not be null");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length = 0;
        while ((length = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, length);
        }
        return baos.toByteArray();
    }

    /**
     * Read inputStream to fill completely the buffer. Throws EOFException if the stream closes
     * before readung enough bytes.
     *
     * @param inputStream
     *         stream to read
     * @param buffer
     *         buffer to fill
     *
     * @throws EOFException
     * @throws IOException
     */
    static public void readFully(InputStream inputStream, byte[] buffer) throws IOException,
            EOFException {
        int read = 0;
        do {
            int cnt = inputStream.read(buffer, read, buffer.length - read);
            if (cnt < 0)
                throw new EOFException();
            read += cnt;
        } while (read < buffer.length);
    }

    /**
     * Read whole stream into the String using UTF-8 encoding.
     *
     * @param inputStream
     *
     * @return result String
     *
     * @throws IOException
     */
    static public String readToString(InputStream inputStream) throws IOException {
        return new String(readFully(inputStream), utf8);
    }

    /**
     * Shortcut: return varargs as array
     *
     * @param objects
     *
     * @return array
     */
    public static Object[] array(Object... objects) {
        return Arrays.copyOf(objects, objects.length);
    }

    /**
     * Shortcut: return varargs as list
     *
     * @param objects
     *
     * @return array
     */
    public static List<String> stringList(Object... objects) {
        List<String> list = new ArrayList<String>();
        for (Object x : objects) {
            list.add(x.toString());
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static <T> ArrayList<T> arrayToList(Object[] array) {
        ArrayList<T> list = new ArrayList<T>();
        for (Object x : array) {
            list.add((T) x);
        }
        return list;
    }

    public static ArrayList<String> arrayToStringList(Object[] array) {
        ArrayList<String> list = new ArrayList<String>();
        for (Object x : array) {
            list.add(x.toString());
        }
        return list;
    }

    /**
     * Convert "key, value" pairs from varargs into a Map.
     *
     * @param args
     *         key, value pairs.Can be 0 length or any even length.
     *
     * @return filled Map instance
     */
    public static HashMap<String, Object> mapFromArray(Object... args) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < args.length; i += 2)
            map.put(args[i].toString(), args[i + 1]);
        return map;
    }

    /**
     * Convert map to {key: value,...} string
     */
    public static <K, V> String mapToString(Map<K, V> map) {
        assert map != null;
        final StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (final Map.Entry<K, V> entry : map.entrySet()) {
            if (first)
                first = false;
            else
                sb.append(", ");

            sb.append(entry.getKey().toString() + ": " + entry.getValue());
        }
        return sb.toString() + "}";
    }

    /**
     * Execute some callable in the separate thread (this implementation). Checked exceptions are
     * logged.
     *
     * @param callable
     */
    public static void later(final Callable callable) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    callable.call();
                } catch (Exception x) {
                    log.e("later: Exception:", x);
                }
            }
        }).start();
    }

    public static Object trimWithEllipsis(String text, int maxLen) {
        if (maxLen < 4)
            maxLen = 4;
        if (text == null)
            text = "null";
        int l = text.length();
        if (maxLen >= l)
            return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    /**
     * Null-aware equality that supports nulls. Object are same if both are nulls, or one is not
     * null and its equals(other) returns true.
     *
     * @param a
     * @param b
     *
     * @return true if a and b are both null or one that is not null .equals(other) is true
     */
    public static boolean same(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a != null)
            return a.equals(b);
        return b.equals(a);
    }

    /**
     * Create temporary folder.
     *
     * @return newly created temp folder
     *
     * @throws IOException
     */
    public static File createTempDirectory()
            throws IOException {
        final File temp;

        temp = File.createTempFile("temp", Long.toString(System.nanoTime()));

        if (!(temp.delete())) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if (!(temp.mkdir())) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return (temp);
    }

    /**
     * By default File#delete fails for non-empty directories, it works like "rm". We need something
     * a little more brutual - this does the equivalent of "rm -r". It will not follow symlinks -
     * that will do no good ;)
     *
     * @param path
     *         Root File Path
     *
     * @return true iff the file and all sub files/directories have been removed
     *
     * @throws FileNotFoundException
     */
    public static boolean deleteRecursive(File path) throws IOException {
        if (!path.exists()) throw new FileNotFoundException(path.getAbsolutePath());
        boolean ret = true;
        if (path.isDirectory()) {
            for (File f : path.listFiles()) {
                if (!isSymlink(f))
                    ret = ret && deleteRecursive(f);
            }
        }
        return ret && path.delete();
    }

    /**
     * Hack. Check that the file is a symlink
     *
     * @param file
     *
     * @return true if it is a symlink
     *
     * @throws IOException
     */
    public static boolean isSymlink(File file) throws IOException {
        if (file == null)
            throw new NullPointerException("File must not be null");
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = file.getParentFile().getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    /**
     * Join string representation of items in the collection with a specified delimiter.
     *
     * @param collection
     *         to convert to strign (toString() will be applied to each item)
     * @param separator
     *
     * @return string representation
     */
    public static <T> String join(Collection<T> collection, String separator) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (T item : collection) {
            if (!first)
                sb.append(separator);
            else
                first = false;
            sb.append(item.toString());
        }
        return sb.toString();
    }

    /**
     * Null-aware difference check.
     *
     * @param x
     * @param y
     *
     * @return true if only one of x and y is null or !x.equals(y)
     */
    public static boolean different(Object x, Object y) {
        return !same(x, y);
    }

    public static <T> ArrayList<T> list(T... objects) {
        ArrayList<T> result = new ArrayList<>(objects.length);
        for (T x : objects)
            result.add(x);
        return result;
    }


    static Boolean testEnv = null;
    /**
     * Check that current method is called from under junit test. Thanks
     * <a href=http://stackoverflow.com/users/351758/janning>Janning</a> for the original idea.
     * Caches result.
     * @return true if it is the test environment.
     */
    public static boolean isJUnitTest() {
        if (testEnv == null) {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            List<StackTraceElement> list = Arrays.asList(stackTrace);
            for (StackTraceElement element : list) {
                if (element.getClassName().startsWith("org.junit.")) {
                    return (testEnv = true);
                }
            }
            testEnv = false;
        }
        return testEnv;
    }
}
