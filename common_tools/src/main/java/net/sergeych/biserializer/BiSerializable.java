/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;

/**
 * The interface of class that can serialize and deserialize itself to/from {@link Binder}. The class implementing that
 * interface <b>must have default constructor, either public, protected, package-protected or private</b>. Also, it is
 * recommended to register it in a static constructor with {@link DefaultBiMapper#registerClass(Class)}.
 * <p>
 * If you need to provide type name alias for {@link BiMapper}, annotate your class with {@link BiType}.
 */
public interface BiSerializable {

    /**
     * reset state of self according to the serialized data and using the specific deserializer. You can use {@link
     * BiDeserializer#getContext()} / {@link BiDeserializer#withContext(Object, BiDeserializer.ContextBlock)} to
     * simplify recreating complex states.
     *
     * @param data         to deserialize from
     * @param deserializer to deserialize components and use context
     */
    public void deserialize(Binder data, BiDeserializer deserializer);

    /**
     * Construct a Binder holding all necessary information to reconstruct state with {@link #deserialize(Binder,
     * BiDeserializer)}.
     *
     * @param serializer serializer to serialize components
     *
     * @return Binder with serialized state data
     */
    public Binder serialize(BiSerializer serializer);
}
