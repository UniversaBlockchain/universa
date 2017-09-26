/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Static interface (shortcut) to the default {@link BiMapper} instance, see {@link BiMapper#getDefaultMapper()}.
 */
public class DefaultBiMapper {

    public static void deserializeInPlace(Map map) {
        new BiDeserializer().deserializeInPlace(map);
    }

    public static <T> T deserialize(Map map) {
        return new BiDeserializer().deserialize(Binder.from(map));
    }

    /**
     * Try to serialize object to {@link Binder} using current set of {@link BiAdapter}. See {@link
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
    public static @NonNull <T> T serialize(Object x) {
        return new BiSerializer().serialize(x);
    }

    public static <T> void registerAdapter(Class<T> klass, BiAdapter adapter) {
        BiMapper.defaultInstance.registerAdapter(klass, adapter);
    }

    /**
     * Register a class with explicit de/serialization methods. See {@link BiSerializable}.
     *
     * @param klass class to register.
     */
    public static void registerClass(Class<? extends BiSerializable> klass) {
        BiMapper.defaultInstance.registerClass(klass);
    }

    static {
        DefaultBiMapper.registerAdapter(ZonedDateTime.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object,BiSerializer serializer) {
                return Binder.fromKeysValues("seconds", ((ZonedDateTime) object).toEpochSecond());
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                return ZonedDateTime.ofInstant(Instant.ofEpochSecond(binder.getLongOrThrow("seconds")),
                                               ZoneOffset.systemDefault());
            }

            @Override
            public String typeName() {
                return "unixtime";
            }
        });
    }

}