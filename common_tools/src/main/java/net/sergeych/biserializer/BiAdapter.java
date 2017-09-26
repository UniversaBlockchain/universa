/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;

public interface BiAdapter<T> {

    Binder serialize(T object, BiSerializer serializer);

    T deserialize(Binder binder, BiDeserializer deserializer);

    default String typeName() {
        return null;
    }
}
