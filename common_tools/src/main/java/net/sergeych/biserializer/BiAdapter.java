/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;

/**
 * Adapter to de/serialize any existing class. Use it when you need to add support for a 3rd aprty class or you just
 * don't want to insert seriazling code into your class. You can get the same easier and supporintg inheritance by
 * implementing {@link BiSerializable} interface.
 * <p>
 * When de/serializing inner parts of your object you should use provided {@link BiSerializer} and {@link
 * BiDeserializer} instances, as they could carry important data, like custom set of adapters to use or {@link
 * BiDeserializer#getContext()}.
 *
 * @param <T> class to de/serialize
 */

public interface BiAdapter<T> {

    /**
     * Serialize into binder. Use parameter to serialize components
     *
     * @param object     to serialize
     * @param serializer to use to serialize inner data (if neeed)
     *
     * @return serialized object
     */
    Binder serialize(T object, BiSerializer serializer);

    /**
     * Reconstruct object using its serialized representation using deserializer for components.
     *
     * @param binder
     * @param deserializer
     *
     * @return
     */
    T deserialize(Binder binder, BiDeserializer deserializer);

    default String typeName() {
        return null;
    }
}
