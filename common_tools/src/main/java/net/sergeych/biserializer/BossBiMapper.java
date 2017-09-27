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
import java.util.Map;

/**
 * Static interface (shortcut) to the default {@link BiMapper} instance, see {@link BossBiMapper#getDefaultMapper()}.
 */
public class BossBiMapper {

    public static void deserializeInPlace(Map map) {
        new BiDeserializer(getInstance()).deserializeInPlace(map);
    }

    private static BiMapper mapper = null;
    private static int lastRevision = 0;

    public static synchronized BiMapper getInstance() {
        BiMapper full = DefaultBiMapper.getDefaultMapper();
        if( mapper == null || lastRevision < full.getRevision() ) {
            mapper = new BiMapper(full);
            lastRevision = full.getRevision();
            mapper.unregister(ZonedDateTime.class);
            mapper.unregister((new byte[0]).getClass());
        }
        return mapper;
    }

    public static <T> T deserialize(Map map) {
        return new BiDeserializer(getInstance()).deserialize(Binder.from(map));
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
        return getSerializer().serialize(x);
    }

    public static <T> void registerAdapter(Class<T> klass, BiAdapter adapter) {
        getInstance().registerAdapter(klass, adapter);
    }

    public static BiDeserializer getDeserializer() {
        return new BiDeserializer(getInstance());
    }

    public static BiSerializer getSerializer() {
        return new BiSerializer(getInstance());
    }
}