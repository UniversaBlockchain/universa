/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.tools;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by sergeych on 15/02/16.
 */
public class Do {

    static Charset utf8 = Charset.forName("utf-8");

    static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static byte[] read(String fileName) throws IOException {
        try (FileInputStream f = new FileInputStream(fileName)) {
            return read(f);
        }
    }

    public static byte[] read(InputStream inputStream) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int l;
        while ((l = inputStream.read(block)) >= 0) {
            bos.write(block, 0, l);
            if(l < block.length)
                break;
        }
        return bos.toByteArray();
    }

    public static String readToString(InputStream inputStream) throws IOException {
        return new String(read(inputStream), utf8);
    }

    public static <A, B> A checkSame(A oldValue, B newValue, Runnable onChanged) {
        if (oldValue != newValue && (oldValue == null || !oldValue.equals(newValue))) {
            onChanged.run();
            return (A) newValue;
        }
        return oldValue;
    }

    /**
     * Get a random item of the collection. Effectively process {@link RandomAccess} enablied {@link List} collections.
     * For other types of collections converts i to an array which might require further optimization.
     *
     * @param source collection
     *
     * @return random item. null if the collection is empty.
     */
    public static <T> T sample(Collection source) {
        int size = source.size();
        if (size <= 0)
            return null;
        int i = Do.randomInt(size);
        if (source instanceof List && source instanceof RandomAccess)
            return (T) ((List) source).get(i);
        else
            return (T) source.toArray()[i];
    }

    static private SecureRandom prng;

    static public SecureRandom getRng() {
        if (prng == null) {
            try {
                prng = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException e) {
                System.out.println("no SHA1PRNG found");
                throw new RuntimeException("no suitable RNG found", e);
            }
        }
        return prng;
    }

    /**
     * get the next integer in [0,max[ (max exclusive, zero inclusive) range using {@link SecureRandom} generator
     *
     * @param max
     *
     * @return
     */
    public static int randomInt(int max) {
        return getRng().nextInt(max);
    }

    /**
     * Get a sequence of random bytes using SecureRandom.
     *
     * @param length array length
     *
     * @return random bytes array
     */
    public static byte[] randomBytes(int length) {
        byte[] data = new byte[length];
        getRng().nextBytes(data);
        return data;
    }

    /**
     * Get a sequence of random negative bytes using SecureRandom, useful to test errors
     *
     * @param length array length
     *
     * @return random bytes array
     */
    public static byte[] randomNegativeBytes(int length) {
        byte[] data = new byte[length];
        getRng().nextBytes(data);
        for (int i = 0; i < data.length; i++) {
            if (data[i] > 0)
                data[i] = (byte) -data[i];
        }
        return data;
    }

    public static int randomIntInRange(int inclusiveMinimum, int inclusiveMaximum) {
        return randomInt(inclusiveMaximum - inclusiveMinimum + 1) + inclusiveMinimum;
    }

    public static byte[] read(File f) throws IOException {
        return Files.readAllBytes(f.toPath());
    }

    /**
     * Interface to single method with no parameters throwing exception. {@link Runnable} replacement when you need
     * allowed exceptions.
     */
    public interface Action {
        void perform() throws Exception;
    }

    public interface Task<T> {
        T perform() throws Exception;
    }

    public interface TaskWithoutResult {
        void perform() throws Exception;
    }

    static public void delay(long millis, Action action) {
        threadPool.execute(
                () -> {
                    try {
                        if (millis > 0)
                            Thread.sleep(millis);
                        action.perform();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    static public void later(Action action) {
        delay(0, action);
    }

    /**
     * Convert "key, value" pairs from varargs into a Map.
     *
     * @param args key, value pairs.Can be 0 length or any even length.
     *
     * @return filled Map instance
     */
    public static HashMap<String, Object> map(Object... args) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < args.length; i += 2)
            map.put(args[i].toString(), args[i + 1]);
        return map;
    }

    public static <T> Collection<T> collection(Object x) {
        if (x instanceof Collection)
            return (Collection<T>) x;
        return list(x);
    }

    /**
     * Convert native array or some collection fo list
     *
     * @param x array, collection or a list
     *
     * @return
     */
    public static <T, U> ArrayList<T> list(U x) {
        if (x instanceof ArrayList)
            return (ArrayList<T>) x;
        if (x instanceof Collection) {
            return new ArrayList<>((Collection) x);
        }
        if (x instanceof Collection) {
            return new ArrayList<>((Collection) x);
        }
        if (x.getClass().isArray()) {
            ArrayList list = new ArrayList();
            int length = Array.getLength(x);
            for (int i = 0; i < length; i++)
                list.add(Array.get(x, i));
            return list;
        }
        return null;
    }

    public static <T, U> ArrayList<T> listOf(U... objects) {
        ArrayList<T> list = new ArrayList<T>();
        for (U x : objects) {
            list.add((T) x);
        }
        return list;
    }

    public static boolean deepEqualityTest(Object a, Object b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        if (a instanceof Number) {
            return b instanceof Number ? ((Number) a).doubleValue() == ((Number) b).doubleValue()
                    : false;
        }
        if (a instanceof String)
            return a.equals(b);
        if (a.getClass().isArray() || a instanceof Collection) {
            Collection<?> ca = Do.collection(a);
            Collection<?> cb = Do.collection(b);
            if (ca.size() != cb.size())
                return false;
            Iterator<?> ita = ca.iterator();
            Iterator<?> itb = cb.iterator();
            while (ita.hasNext()) {
                if (!deepEqualityTest(ita.next(), itb.next()))
                    return false;
            }
            return true;
        }
        if (a instanceof Map) {
            Map<?, ?> ma = (Map) a;
            Map<?, ?> mb = (Map) b;
            if (ma.size() != mb.size())
                return false;
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!deepEqualityTest(e.getValue(), mb.get(e.getKey())))
                    return false;
            }
            return true;
        }
        return false;
    }

    public static byte[] decodeBase64(String base64) {
        String baseStr = base64.replace("\n", "").replace("\t", "").replace(" ", "").replace
                ("\r", "");
        int n = baseStr.length() % 4;
        if (n > 0) {
            StringBuilder sb = new StringBuilder(baseStr);
            while (n++ < 4)
                sb.append('=');
            return net.sergeych.utils.Base64.decodeLines(sb.toString());
        } else
            return net.sergeych.utils.Base64.decodeLines(baseStr);
    }

    /**
     * Immediately start specified task (e.g. block returning something and throwing some exception on failure) in a
     * separate thread. Use {@link DeferredResult} to get the results. Meant to start operations that have to be done in
     * parallel.
     *
     * @param task to perform
     *
     * @return connected and deferred result of the operation already started.
     */
    static public DeferredResult inParallel(final Task task) {
        final DeferredResult deferredResult = new DeferredResult();
        threadPool.execute(() -> {
            try {
                deferredResult.sendSuccess(task.perform());
            } catch (Exception e) {
                deferredResult.sendFailure(e);
            }
        });
        return deferredResult;
    }

    static public DeferredResult inParallel(final TaskWithoutResult task) {
        return Do.inParallel(() -> {
            task.perform();
            return null;
        });
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Convert a snake_case_string to CamelCaseString. The first character will always be in upper case
     *
     * @param snakeString
     * @return converted string
     */
    public static String snakeToCamelCase(String snakeString) {
        StringBuilder b = new StringBuilder();
        for(String s: snakeString.split("_") ) {
            if(s.length() > 0 ) {
                b.append(Character.toUpperCase(s.charAt(0)));
                b.append(s.substring(1, s.length()).toLowerCase());
            }
        }
        return b.toString();
    }

}
