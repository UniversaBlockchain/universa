package com.icodici.universa.contract;

import com.icodici.universa.HashId;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.util.ArrayList;

public class ReferenceRole implements BiSerializable {
    public String name;
    public byte[] fingerprint;

    public ReferenceRole(String name, byte[] fingerprint) {
        this.name = name;
        this.fingerprint = fingerprint;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.name = data.getString("name", null);

        this.fingerprint = data.getBinary("type");
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder data = new Binder();
        data.set("name", s.serialize(this.name));
        data.set("fingerprint", s.serialize(this.fingerprint));
        return data;
    }

    static {
        DefaultBiMapper.registerClass(ReferenceRole.class);
    }
}
