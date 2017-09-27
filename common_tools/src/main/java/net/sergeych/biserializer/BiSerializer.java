/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

/**
 * The serializer to convert objects and plain old data to binder-serialized form. See {@link BiMapper} for more.
 */
public class BiSerializer {
    protected BiMapper mapper;

    /**
     * Create serializer to use with a given mapper.
     *
     * @param mapper
     */
    public BiSerializer(BiMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Create serialized to work with default mapper, {@link DefaultBiMapper#getInstance()}.
     */
    public BiSerializer() {
        this.mapper = DefaultBiMapper.getInstance();
    }

    /**
     * Serialize any object. See {@link BiMapper#deserializeObject(Object, BiDeserializer)} for more information.
     *
     * @param obj
     * @param <T>
     *
     * @return
     */
    public <T> T serialize(Object obj) {
        return mapper.serialize(obj, this);
    }

    /**
     * Try do serialize some object. Throws an exception if serialized form is tha same as not serialzied, as it means
     * that there is no suitable adapter to process. Be caeful because it will throw an exception also for the types
     * should not be serialized and this way are left intact.
     *
     * @param obj to try to serialize
     * @param <T>
     *
     * @return
     */
    public <T> T serializeOrThrow(Object obj) {
        T result = mapper.serialize(obj, this);
        if (result == obj)
            throw new BiSerializationException("can't serialize " + obj);
        return result;
    }
}
