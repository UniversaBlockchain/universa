package com.icodici.universa.contract.services;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnsName implements BiSerializable {
    private String unsReducedName;
    private String unsName;
    private String unsDescription;

    public static final String NAME_FIELD_NAME = "name";
    public static final String NAME_REDUCED_FIELD_NAME = "reduced_name";
    public static final String DESCRIPTION_FIELD_NAME = "description";

    public UnsName() {}

    public UnsName(String name, String description) {
        unsName = name;
        unsDescription = description;
    }

    protected UnsName initializeWithDsl(Binder binder) {
        unsName = binder.getString(NAME_FIELD_NAME, null);
        unsDescription = binder.getString(DESCRIPTION_FIELD_NAME, null);

        return this;
    }

    public String getUnsReducedName() { return unsReducedName; }
    public String getUnsName() { return unsName; }
    public String getUnsDescription() { return unsDescription; }

    public void setUnsReducedName(String nameReduced) { unsReducedName = nameReduced; }
    public void setUnsName(String name) { unsName = name; }
    public void setUnsDescription(String description) { unsDescription = description; }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        if(unsReducedName != null) {
            data.set(NAME_REDUCED_FIELD_NAME, serializer.serialize(unsReducedName));
        }

        data.set(NAME_FIELD_NAME, serializer.serialize(unsName));
        data.set(DESCRIPTION_FIELD_NAME, serializer.serialize(unsDescription));

        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.unsReducedName = data.containsKey(NAME_REDUCED_FIELD_NAME) ? data.getString(NAME_REDUCED_FIELD_NAME) : null;
        this.unsName = data.getString(NAME_FIELD_NAME);
        this.unsDescription = data.getString(DESCRIPTION_FIELD_NAME);
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof UnsName)) {
            return false;
        }

        return unsName.equals(((UnsName) obj).unsName)
                && unsReducedName.equals(((UnsName) obj).unsReducedName)
                && unsDescription.equals(((UnsName) obj).unsDescription);
    }

    public boolean equalsTo(NameRecord nr) {
        return unsName.equals(nr.getName())
                && unsReducedName.equals(nr.getNameReduced())
                && unsDescription.equals(nr.getDescription());

    }

    static {
        Config.forceInit(UnsName.class);
        Config.forceInit(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsName.class);
    }
}
