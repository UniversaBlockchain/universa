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
        this.mapper = BiMapper.getDefaultMapper();
    }

    public <T> T serialize(Object obj) {
        return (T) mapper.serialize(obj, this);
    }
}
