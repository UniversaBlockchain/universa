/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

public class BiSerializer {
    protected BiMapper mapper;

    public BiSerializer(BiMapper mapper) {
        this.mapper = mapper;
    }

    public BiSerializer() {
        this.mapper = DefaultBiMapper.getDefaultMapper();
    }

    public <T> T serialize(Object obj) {
        return mapper.serialize(obj, this);
    }

    public <T> T serializeOrThrow(Object obj) {
        T result = mapper.serialize(obj, this);
        if( result == obj )
            throw new BiSerializationException("can't serialize "+obj);
        return result;
    }
}
