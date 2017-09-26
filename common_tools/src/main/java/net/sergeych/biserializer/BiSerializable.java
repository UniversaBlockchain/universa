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
 * interface <b>must have default constructor, either public or private</b>. Also, it is recommended to register it in a
 * static constructor with {@link DefaultBiMapper#registerClass(Class)}.
 * <p>
 * If you need to provide type name alias for {@link BiMapper}, annotate your class with {@link
 * BiType}.
 */
public interface BiSerializable {
    public void deserialize(Binder data,BiDeserializer deserializer);

    public Binder serialize(BiSerializer s);
}
