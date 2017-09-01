/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.tools;

import net.sergeych.utils.Bytes;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Created by sergeych on 02/12/16.
 */
public class Binder extends HashMap<String, Object> {

    /**
     * An empty, unmodifiable Binder can be used as a returned value, for example, in an effective way
     */
    public static final Binder EMPTY;

    static {
        EMPTY = new Binder();
        EMPTY.freeze();
    }

    private boolean frozen = false;

    /**
     * Convert "key, value" pairs from varargs into a Params.
     *
     * @param args key, value pairs.Can be 0 length or any even length.
     *
     * @return filled Map instance
     */
    static public Binder fromKeysValues(Object... args) {
        Binder map = new Binder();
        for (int i = 0; i < args.length; i += 2)
            map.put(args[i].toString(), args[i + 1]);
        return map;
    }

    public Binder(Map copyFrom) {
        putAll(copyFrom);
    }

    public Binder() {
    }

    public boolean isFrozen() {
        return frozen;
    }

    protected void checkNotFrozen() {
        if (frozen)
            throw new IllegalStateException("attempt to modify a frozen binder");
    }

    public Binder(Object... keyValuePairs) {
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
    }

    public Double getDouble(String key) {
        Object x = get(key);
        if (x instanceof String)
            return Double.parseDouble((String) x);
        return new Double((double) x);
    }

    /**
     * Get the parameter as string. Throws exception if it is missing.
     *
     * @param key
     *
     * @return string paramter
     *
     * @throws IllegalArgumentException if the parameter does not exist
     */
    public String getStringOrThrow(String key) throws IllegalArgumentException {
        Object object = get(key);
        if (object == null)
            throw new IllegalArgumentException("missing required parameter: " + key);
        return object.toString();
    }

    public String getString(String key, String defaultValue) {
        Object result = get(key);
        return result == null ? defaultValue : result.toString();
    }

    /**
     * Compatibility method. Same as {@link #getStringOrThrow(String)}.
     *
     * @throws IllegalArgumentException
     */
    public String getString(String key) throws IllegalArgumentException {
        return getStringOrThrow(key);
    }


    /**
     * Get the binary as byte[]. If the data are in the improper format, throws exception.
     *
     * @param key
     *
     * @return data or null if key is missing
     */
    public byte[] getBinary(String key) {
        Object x = get(key);
        if (x == null)
            return null;
        if (x instanceof Bytes)
            return ((Bytes) x).toArray();
        if (x instanceof byte[])
            return (byte[]) x;
        throw new ClassCastException("parameter can't be converted to byte[]");
    }

    /**
     * Get the binary parameter as byte[] and throws exception if it is missing
     *
     * @param key
     *
     * @return
     *
     * @throws IllegalArgumentException
     */
    public @Nonnull
    byte[] getBinaryOrThrow(String key) throws IllegalArgumentException {
        byte[] binary = getBinary(key);
        if (binary == null)
            throw new IllegalArgumentException("missing requirend parameter: " + key);
        return binary;
    }

    /**
     * Get the boolean value, return false if not found, same as if found 'false'. We recommend to use either {@link
     * #getBoolean(String, Boolean)} } or {@link #getBinaryOrThrow(String)} to resolve this ambigity.
     *
     * @param key
     *
     * @return
     */
    @Deprecated
    public boolean getBoolean(String key) {
        Boolean res = (Boolean) get(key);
        return res == null ? false : (boolean) res;
    }

    /**
     * Get the value as boolean, with given default value if it is missed
     *
     * @param key          name of the parameter
     * @param defaultValue value to return if missing
     *
     * @return value found in mao or the defaultValue
     */
    public Boolean getBoolean(String key, Boolean defaultValue) {
        Boolean res = (Boolean) get(key);
        return (res == null) ? defaultValue : res;
    }

    /**
     * Get the boolean value of the specivied key or throw
     *
     * @param key to search
     *
     * @return value as boolean
     *
     * @throws IllegalArgumentException if key not found
     */
    public Boolean getBooleanOrThrow(String key) throws IllegalArgumentException {
        Boolean result = getBoolean(key, null);
        if (result == null)
            throw new IllegalArgumentException("missing key " + key);
        return result;
    }

    /**
     * Get the sub-hash with the given name. Works even if there is no such sub-params:
     * <p>
     * <code>params.of("some").of("non-existing").of("path").getStringOrThrow("foo") == null</code>
     * <p>
     * will work as expected.
     *
     * @param key key name
     *
     * @return either the sub-hash or new empty hash.
     */
    public Binder of(String key) {
        HashMap<String, Object> x = (HashMap<String, Object>) get(key);
        Binder b = null;
        if (x instanceof Binder) {
            b = (Binder) x;
        } else
            b = x != null ? new Binder(x) : new Binder();
        if (frozen)
            b.freeze();
        return b;
    }

    public Binder getOrCreateBinder(String key) {
        checkNotFrozen();
        HashMap<String, Object> x = (HashMap<String, Object>) get(key);
        Binder b = null;
        if (x == null) {
            b = new Binder();
            put(key, b);
        } else
            b = (x instanceof Binder) ? (Binder) x : new Binder(x);
        return b;

    }

    /**
     * Return Binder for the key, or empty binder if it does not exist.
     *
     * @param key
     *
     * @return
     */
    public Binder getBinder(String key) {
        return of(key);
    }

    public Binder getBinderOrThrow(String key) {
        Binder result = getBinder(key, null);
        if (result == null)
            throw new IllegalArgumentException("map not found at key: " + key);
        return result;
    }

    public Binder getBinder(String key, Binder defaultValue) {
        HashMap<String, Object> x = (HashMap<String, Object>) get(key);
        Binder b = null;
        if (x instanceof Binder) {
            b = (Binder) x;
        } else if (x != null) {
            b = new Binder(x);
            if (frozen)
                b.freeze();
        }
        return b == null ? defaultValue : b;
    }

    /**
     * Use {@link #getIntOrThrow(String)} instead.
     *
     * @param key parameter name
     *
     * @return parameter value or throws {@link IllegalArgumentException}.
     */
    @Deprecated
    public int getInt(String key) {
        return getIntOrThrow(key);
    }

    /**
     * @param key parameter name
     *
     * @return parameter value or throws {@link IllegalArgumentException}.
     */
    public int getIntOrThrow(String key) {
        Number n = (Number) get(key);
        if (n == null) throw new IllegalArgumentException("missing integer parameter");
        return n.intValue();
    }

    /**
     * Use {@link #getLongOrThrow(String)} instead.
     *
     * @param key
     *
     * @return
     */
    @Deprecated
    public long getLong(String key) {
        Number i = (Number) get(key);
        if (i == null) throw new IllegalArgumentException("missing long integer parameter");
        return i.longValue();
    }

    public long getLong(String key, long defaultValue) {
        Number i = (Number) get(key);
        return (i == null) ? defaultValue : i.longValue();
    }

    public Integer getInt(String key, Integer defaultValue) {
        Object o = get(key);
        if (o == null)
            return defaultValue;
        if (o instanceof String) {
            return Integer.valueOf((String) o);
        } else if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        throw new IllegalArgumentException("can't convert to integer: " + o.getClass().getCanonicalName());
    }

    public ArrayList<?> getArray(String key) {
        Object x = get(key);
        return x == null ? new ArrayList<>() : Do.list(x);
    }

    public ArrayList<Binder> getBinders(String key) {
        Object o = get(key);
        if (o instanceof ArrayList)
            return (ArrayList<Binder>) o;
        ArrayList<Binder> result = new ArrayList<>();
        for (Object x : getArray(key)) {
            if (x instanceof Binder)
                result.add((Binder) x);
            else
                result.add(new Binder((Map<String, Object>) x));
        }
        return result;
    }

    /**
     * Add Binder as a field with the specified index and return it in a typed manner. Sort of the syntax sugar.
     *
     * @param key
     * @param object
     *
     * @return
     */
    public <T extends Object> T set(String key, T object) {
        checkNotFrozen();
        put(key, object);
        return (T) object;
    }

    public Binder unmodifiableCopy() {
        Binder b = new Binder(this);
        b.freeze();
        return b;
    }

    public void freeze() {
        frozen = true;
    }

    /**
     * Convert some map to the binder. Do nothing if it is already a binder.
     *
     * @param x source map
     */
    static public Binder from(Object x) {
        return (x instanceof Binder) ? (Binder) x : new Binder((Map) x);
    }

    /**
     * Convert some map to the binder. Do nothing if it is already a binder.
     *
     * @param x source map
     */
    static public Binder of(Object x) {
        return from(x);
    }

    /**
     * Put several keys and values from array varargs. Sample: <code>setKeysValues("foo", 1, "bar", 2);</code>
     *
     * @param keyValueParis
     *
     * @return self
     */
    public Binder putAll(Object... keyValueParis) {
        if ((keyValueParis.length & 1) == 1)
            throw new IllegalArgumentException("keyValuePairs should be even sized array");
        for (int i = 0; i < keyValueParis.length; i += 2) {
            String key = keyValueParis[i].toString();
            put(key, keyValueParis[i + 1]);
        }
        return this;
    }

    public LocalDateTime getLocalDateTime(String key, LocalDateTime defaultValue) {
        Object obj = get(key);
        if (obj == null)
            return defaultValue;
        if (obj instanceof LocalDateTime)
            return (LocalDateTime) obj;
        if (obj instanceof Date)
            return LocalDateTime.ofEpochSecond(((Date) obj).getTime() / 1000, 0, ZoneOffset.UTC);
        if (obj instanceof Number)
            return LocalDateTime.ofEpochSecond(((Number) obj).longValue(), 0, ZoneOffset.UTC);
        if (obj instanceof Map) {
            Map<String, Object> map = (Map) obj;
            String t = (String) map.get("__type");
            if (t == null)
                t = (String) map.get("__t");
            long ss = (int) map.get("seconds");
            if (t != null) {
                return Instant.ofEpochMilli(ss * 1000).atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        }
        if (obj.equals("now()"))
            return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        throw new IllegalArgumentException("can't convert key " + key + " to LocalDateTime: " + obj);
    }

    public LocalDateTime getLocalDateTimeOrThrow(String key) {
        LocalDateTime t = getLocalDateTime(key, null);
        if (t == null)
            throw new IllegalArgumentException("missing key:" + key);
        return t;
    }

    public long getLongOrThrow(String key) {
        Number i = (Number) get(key);
        if (i == null) throw new IllegalArgumentException("missing long integer parameter: " + key);
        return i.longValue();
    }

    public Bytes getBytesOrThrow(String key) {
        Bytes bb = getBytes(key, null);
        if (bb == null)
            throw new IllegalArgumentException("missing reauired Bytes parameter: " + key);
        return bb;
    }

    private Bytes getBytes(String key, Bytes defaultValue) {
        Object x = get(key);
        if (x instanceof Bytes)
            return (Bytes) x;
        if (x instanceof byte[])
            return new Bytes((byte[]) x);
        throw new IllegalArgumentException("can't convert to Bytes: " + x);
    }

    /**
     * Get object at key or throw {@link IllegalArgumentException}. Important. This method throw exception if the value
     * not found in the map, or if the value is null, and also if the value can't be casted to T. do not use it if nulls
     * are valid values.
     *
     * @param key
     * @param <T>
     *
     * @return
     */
    public <T> T getOrThrow(String key) {
        T result = (T) get(key);
        if (result == null)
            throw new IllegalArgumentException("missing key: " + key);
        return result;
    }

    /**
     * Get object as list, or create empty list if it is empty
     *
     * @param key
     * @param <T>
     *
     * @return list instance, not null.
     */
    public <T> List<T> getOrCreateList(String key) {
        Object x = get(key);
        if (x == null)
            return new ArrayList<>();
        return Do.list(x);
    }

    public <T> List<T> getList(String key, List<T> defaultValue) {
        Object x = get(key);
        if (x == null)
            return null;
        List<T> list = Do.list(x);
        if (x == null)
            throw new IllegalArgumentException("can't make list for " + key + " from " + x);
        return list;

    }

    public <T> List<T> getListOrThrow(String key) {
        List<T> list = getList(key, null);
        if (list == null)
            throw new IllegalArgumentException("missing list parameter: " + key);
        return list;
    }

    /**
     * Add some delta to a numeric field with a given name  that must exist and be convertible to integer. After hte
     * operation the modified value of type Integer is stored in this binder
     *
     * @param name
     * @param delta
     *
     * @return new value
     */
    public int addToInt(String name, int delta) {
        int value = getIntOrThrow(name);
        value += delta;
        put(name, value);
        return value;
    }
}
