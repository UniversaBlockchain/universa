/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Default {@link BiMapper} provider to be used with the {@link Boss} protocol. It uses all
 * serialization rules known to {@link DefaultBiMapper}, excepting some types natively supported by {@link
 * net.sergeych.boss.Boss}:
 * <p>
 * <ul>
 * <p>
 * <li>it does not serialize {@link ZonedDateTime}</li>
 * <p>
 * <li>it does not serialize binary data (byte[])</li>
 * <p>
 * </ul>
 */
public class BossBiMapper {

    public static void deserializeInPlace(Map map) {
        new BiDeserializer(getInstance()).deserializeInPlace(map);
    }

    private static BiMapper mapper = null;
    private static int lastRevision = 0;

    static {
        recalculateMapper();
    }

    public static BiMapper getInstance() {
        return mapper;
    }

    public static void recalculateMapper() {
        BiMapper full = DefaultBiMapper.getInstance();
        if (mapper == null || lastRevision < full.getRevision()) {
            BiMapper m = new BiMapper(full);
            lastRevision = full.getRevision();
            m.unregister(ZonedDateTime.class);
            m.unregister((new byte[0]).getClass());
            mapper = m;
        }
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
        return newSerializer().serialize(x);
    }

    /**
     * Register serialization adapter in the default Boss mapper only. See {@link BiMapper#registerAdapter(Class,
     * BiAdapter)} for more information.
     *
     * @param klass
     * @param adapter
     */
    public static void registerAdapter(Class<?> klass, BiAdapter adapter) {
        getInstance().registerAdapter(klass, adapter);
    }

    /**
     * Register serializabble class in the default Boss mapper only. See {@link BiMapper#registerClass(Class)} for more
     * information.
     *
     * @param klass
     * @param adapter
     */
    public static void registerClass(Class<? extends BiSerializable> klass) {
        getInstance().registerClass(klass);
    }

    /**
     * Create new deserializer based on default {@link Boss} - optimized {@link BiMapper}.
     * @return
     */
    public static BiDeserializer newDeserializer() {
        return new BiDeserializer(getInstance());
    }

    /**
     * Create new serializer based on default {@link Boss} - optimized {@link BiMapper}.
     * @return
     */
    public static BiSerializer newSerializer() {
        return new BiSerializer(getInstance());
    }
}