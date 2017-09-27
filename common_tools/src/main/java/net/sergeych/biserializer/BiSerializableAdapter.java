/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;

import java.lang.reflect.Constructor;

/**
 * Adapter that de/serializes classes implementing BiSerializable. For internal use by this package.
 */
class BiSerializableAdapter implements BiAdapter {

    private Class<? extends BiSerializable> objectClass;
    private String typeAlias = null;

    public BiSerializableAdapter(Class<? extends BiSerializable> objectClass) {
        this.objectClass = objectClass;
        BiType tn = objectClass.getAnnotation(BiType.class);
        if (tn != null) {
            typeAlias = tn.name();
        }
    }

    public String typeName() {
        return typeAlias == null ? objectClass.getCanonicalName() : typeAlias;
    }

    @Override
    public Binder serialize(Object object,BiSerializer s) {
        return ((BiSerializable) object).serialize(s);
    }

    @Override
    public Object deserialize(Binder binder, BiDeserializer deserializer) {
        try {
            Constructor c= objectClass.getDeclaredConstructor();
            c.setAccessible(true);
            BiSerializable bs = (BiSerializable) c.newInstance();
            bs.deserialize(binder, deserializer);
            return bs;
        } catch (Exception e) {
            throw new BiSerializationException("failed to deserialize " + typeName()
                                                       + ": " + objectClass.getCanonicalName(), e);
        }
    }
}
