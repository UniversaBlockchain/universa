/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper allows sny object types to have registered procedures to de/serialize self to the Map structures, e.g.
 * Binders.
 * <p>
 * To provide support for your own class, implement {@link BiSerializable} in it and register it with {@link
 * #registerClass(Class)}, in a static constructor or explicitly. If you can't or don't like to integrate it into the
 * class, write custom {@link BiAdapter} for it and register it with {@link #registerAdapter(Class, BiAdapter)}.
 * <p>
 * There is a {@link DefaultBiMapper} to reigister all general-purpose classes and adapters. If you don't want to expose
 * it globally, create custom {@link BiMapper} instance and register your private stuff with it, then get {@link
 * #newSerializer()} and {@link #newDeserializer()} or construct these directly using your private mapper.
 * <p>
 * This could be tricky as it can not reconstruct reference trees, unlike, for example, {@link net.sergeych.boss.Boss}
 * serializer, so your serialization code must do its best to properly reconstruct links in your object tree.
 */
public class BiMapper {
    private HashMap<String, BiAdapter> adapters = new HashMap<String, BiAdapter>();
    private int revision = 0;

    /**
     * Create empty mapper, knowing nothing about serialization. Populate it with {@link #registerAdapter(Class,
     * BiAdapter)} and/or {@link #registerClass(Class)} before use.
     */
    public BiMapper() {
    }

    /**
     * Create a mapper with copy of serialization information from a given mapper. Note that the class receives a copy
     * of it, so later changes in a parent class won't affect this instance and vice versa.
     *
     * @param parent mapper to copy serialization information from
     */
    public BiMapper(BiMapper parent) {
        adapters.putAll(parent.adapters);
    }

    /**
     * Update the map deserialziing it's values - e.g. in place. Saves memory but has strong side effects of discarding
     * source data.
     *
     * @param map          to update
     * @param deserializer to use to deserialize map values
     */
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
            } else if (value instanceof Collection) {
                map.put(key,
                        ((Collection) value).stream()
                                .map(x -> deserializer.deserialize(x))
                                .collect(Collectors.toList())
                );
            }
        });
    }

    /**
     * Try to deserialize map containing serialized object, e.g. having "__type" or "__t" key. Important. Is this
     * implementation can't deserialize the map, what usually means it has no known/properly serialized object in it, it
     * juts desearliazes it in place with {@link #deserializeInPlace(Map, BiDeserializer)} and return the same (but
     * updated) map instance.
     *
     * @param map possible containing the serialized object
     * @param <T>
     *
     * @return deserialized object or deserialized map
     */
    public <T> T deserialize(Map map, BiDeserializer deserializer) {
        String typeName = (String) map.get("__type");
        if (typeName == null)
            typeName = (String) map.get("__t");
        if (typeName != null) {
            BiAdapter adapter = adapters.get(typeName);
            if (adapter != null) {
                return (T) adapter.deserialize(Binder.from(map), deserializer);
            }
        }
        deserializeInPlace(map, deserializer);
        return (T) map;
    }

    /**
     * {@link #deserialize(Map, BiDeserializer)} using default {@link BiDeserializer}.
     *
     * @param map to deserialize
     * @param <T>
     *
     * @return deserialized object of the map with deserialized values.
     */
    public <T> T deserialize(Map map) {
        return deserialize(map, new BiDeserializer(this));
    }

    public <T> T deserializeObject(Object x) {
        return x == null ? null : deserializeObject(x, new BiDeserializer(this));
    }

    /**
     * Deserialize any object, if possible.
     * <p>
     * <ul> <li>if the object is a simple type, e.g. number, string, boolean, it will be simply retured.</li> <li>if the
     * object is a collection, every item of it will be deserialized the sam way and the result will be returned as a
     * List with random access (like ArrayList)</li> <li>if the object is a map, it will be processed as with {@link
     * #deserialize(Map, BiDeserializer)}</li> </ul>
     *
     * @param obj          to deserealize
     * @param deserializer to
     * @param <T>
     *
     * @return
     */
    public <T> T deserializeObject(Object obj, BiDeserializer deserializer) {
        if (obj instanceof String || obj instanceof Number || obj instanceof Boolean
                || obj instanceof ZonedDateTime || obj instanceof Bytes)
            return (T) obj;
        if (obj instanceof Map)
            return deserialize((Map) obj, deserializer);
        if (obj instanceof Collection) {
            return (T) ((Collection) obj).stream()
                    .map(x -> deserializeObject(x))
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException("don't know how to deserealize " + obj.getClass().getCanonicalName());
    }

    /**
     * Try to serialize object to {@link Binder} using current set of {@link BiAdapter}. See {@link
     * #registerAdapter(Class, BiAdapter)}, {@link #registerClass(Class)} for more. This method properly serializes Maps
     * and Collections, serializing it contents.
     *
     * @param x   object to serialize (can be array, list, map, binder or any object with registered adapter. processes
     *            in depth, e.g. all values in the map or items in the list.
     * @param <T>
     *
     * @return either a Binder or a simple object x (e.g. String if x instanceof String).
     *
     * @throws IllegalArgumentException if unkonwn ibject ecnountered which can not be serialized.
     */
    public @NonNull <T> T serialize(Object x, BiSerializer serializer) {
        if (x instanceof String || x instanceof Number || x instanceof Boolean || x == null)
            return (T) x;
        Class<?> klass = x.getClass();
        if (klass.isArray() && !(klass.getComponentType() == byte.class)) {
            x = Arrays.asList((Object[]) x);
        }
        if (x instanceof Collection) {
            return (T) ((Collection) x).stream()
                    .map(i -> serialize(i, serializer))
                    .collect(Collectors.toList());

        }
        String canonicalName = klass.getCanonicalName();
        BiAdapter adapter = adapters.get(canonicalName);
        if (adapter == null) {
            if (x instanceof Map) {
                ((Map) x).replaceAll((k, v) -> serialize(v, serializer));
                return (T) x;
            }
            // just leave it as it is
            return (T) x;
//            throw new IllegalArgumentException("can't convert to binder " + canonicalName + ": " + x);
        }
        Binder result = adapter.serialize(x, serializer);
        String tn = adapter.typeName();
        result.put("__type", tn != null ? tn : canonicalName);
        return (T) result;
    }

    /**
     * Same as {@link #serialize(Object, BiSerializer)} with default {@link BiSerializer}.
     */
    public @NonNull <T> T serialize(Object x) {
        return serialize(x, new BiSerializer(this));
    }

    public <T> void registerAdapter(Class<T> klass, BiAdapter adapter) {
        adapters.put(klass.getCanonicalName(), adapter);
        String typeName = adapter.typeName();
        if (typeName != null)
            adapters.put(typeName, adapter);
        revision++;
    }

    int getRevision() {
        return revision;
    }

    /**
     * unregister class. All associated adapters will be removed.
     *
     * @param klass
     *
     * @return true if the class was registered before, wither as {@link BiSerializable} class or with {@link
     *         BiAdapter}.
     */
    public boolean unregister(Class klass) {
        String key = klass.getCanonicalName();
        BiAdapter a = adapters.get(key);
        if (a == null)
            return false;
        adapters.remove(key);
        key = a.typeName();
        if (key != null)
            adapters.remove(key);
        revision++;
        return true;
    }


    /**
     * Add a class to serializable with this mapper. Remeber to have nonparametric constructor (possibly private)
     * so deserializer could create its instances.
     *
     * @param klass to register
     */
    public void registerClass(Class<? extends BiSerializable> klass) {
        registerAdapter(klass, new BiSerializableAdapter(klass));
    }

    /**
     * Construct new serializer to use this mapper
     */
    public BiSerializer newSerializer() {
        return new BiSerializer(this);
    }

    /**
     * Construct new deserializer to use this mapper
     */
    public BiDeserializer newDeserializer() {
        return new BiDeserializer(this);
    }
}