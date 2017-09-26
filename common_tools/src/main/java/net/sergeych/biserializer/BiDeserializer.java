/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;

import java.util.Map;

public class BiDeserializer {
    protected BiMapper mapper;

    public BiDeserializer(BiMapper mapper) {
        this.mapper = mapper;
    }

    public BiDeserializer() {
        mapper = BiMapper.getDefaultMapper();
    }

    public <T> T deserialize(Binder data) {
        return (T) mapper.deserialize(data, this);
    }

    public void deserializeInPlace(Map map) {
        mapper.deserializeInPlace(map, this);
    }
}
