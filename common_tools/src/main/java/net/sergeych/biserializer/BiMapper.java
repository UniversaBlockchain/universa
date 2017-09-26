/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Map and unap (ro serialize and deserialize) any objects to/from Binder hashes.
 *
 * This could be tricky as it can not reconstruct reference trees, unlike, for example, {@link net.sergeych.boss.Boss}
 * serializer, so your serialization code must do its best to properly reconstruct links in your object tree.
 */
public class BiMapper {
    private HashMap<String, BiAdapter> adapters = new HashMap<String, BiAdapter>();


    public void deserializeInPlace(Map map, BiDeserializer deserializer) {
        map.forEach((key, value) -> {
            if (value instanceof Map) {
                String typeName = (String) ((Map) value).get("__type");
                if (typeName == null)
                    typeName = (String) ((Map) value).get("__t");
                if (typeName == null)
                    deserializeInPlace((Map) value, deserializer);
                else {
                    BiAdapter adapter = adapters.get(typeName);
                    if (adapter != null) {
                        map.put(key, adapter.deserialize(Binder.from(value), deserializer));
                    }
                }
            }
        });
    }

    /**
     * Try to deserialize map containing serialized object, e.g. having "__type" or "__t" key. Important. Is this
     * implementation can't deserialize the map, it desearliazes it in place and return it.
     *
     * @param map containing the serialized object
     * @param <T>
     * @return source map or deserialized object
     */
    public <T> T deserialize(Map map,BiDeserializer deserializer) {
        String typeName = (String) map.get("__type");
        if (typeName == null)
            typeName = (String) map.get("__t");
        if (typeName != null) {
            BiAdapter adapter = adapters.get(typeName);
            if (adapter != null) {
                return (T) adapter.deserialize(Binder.from(map), deserializer);
            }
        }
        deserializeInPlace(map,deserializer);
        return (T) map;
    }

    public <T> T deserializeObject(Object obj,BiDeserializer deserializer) {
        if( obj instanceof String || obj instanceof Number || obj instanceof Boolean
                || obj instanceof ZonedDateTime )
            return (T)obj;
        if( obj instanceof Map )
            return deserialize((Map)obj, deserializer);
        throw new IllegalArgumentException("don't know how to deserealize "+obj);
    }

    /**
     * Try to serialize object to {@link Binder} using curretn set of {@link BiAdapter}. See {@link
     * #registerAdapter(Class, BiAdapter)} for more.
     *
     * @param x   object to serialize (can be array, list, map, binder or any object with registered adapter. processes
     *            in depth, e.g. all values in the map or items in the list.
     * @param <T>
     *
     * @return either a Binder or a simple object x (e.g. String if x instanceof String).
     *
     * @throws IllegalArgumentException if unkonwn ibject ecnountered which can not be serialized.
     */
    public @NonNull <T> T serialize(Object x,BiSerializer serializer) {
        if (x instanceof String || x instanceof Number || x instanceof Boolean || x == null)
            return (T) x;
        Class<?> klass = x.getClass();
        if (klass.isArray() && !(klass.getComponentType() == byte.class )) {
            x = Arrays.asList(x);
        }
        if (x instanceof Collection) {
            return (T) ((Collection) x).stream()
                    .map(i -> serialize(i,serializer))
                    .collect(Collectors.toList());

        }
        String canonicalName = klass.getCanonicalName();
        BiAdapter adapter = adapters.get(canonicalName);
        if (adapter == null) {
            if (x instanceof Map) {
                Binder b = new Binder((Map) x);
                b.replaceAll((String k, Object v) -> serialize(v,serializer));
                return (T) b;
            }
            throw new IllegalArgumentException("can't convert to binder " + canonicalName + ": " + x);
        }
        Binder result = adapter.serialize(x, serializer);
        String tn = adapter.typeName();
        result.put("__type", tn != null ? tn : canonicalName );
        return (T) result;
    }

    public <T> void registerAdapter(Class<T> klass, BiAdapter adapter) {
        adapters.put(klass.getCanonicalName(), adapter);
        String typeName = adapter.typeName();
        if (typeName != null)
            adapters.put(typeName, adapter);
    }

    public void registerClass(Class<? extends BiSerializable> klass) {
        registerAdapter( klass, new BiSerializableAdapter(klass));
    }
}