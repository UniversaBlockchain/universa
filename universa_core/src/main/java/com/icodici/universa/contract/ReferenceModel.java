package com.icodici.universa.contract;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.util.ArrayList;
import java.util.List;

public class ReferenceModel implements BiSerializable {
    public String name = "";
    public int type = TYPE_EXISTING;
    public String transactional_id = "";
    public HashId contract_id = null;
    public boolean required = true;
    public HashId origin = null;
    public List<Role> signed_by = new ArrayList<>();

    public static final int TYPE_TRANSACTIONAL = 1;
    public static final int TYPE_EXISTING = 2;

    @Override
    public String toString() {
        String res = "{";
        res += "name:"+name;
        res += ", type:"+type;
        if (transactional_id.length() > 8)
            res += ", transactional_id:"+transactional_id.substring(0, 8)+"...";
        else
            res += ", transactional_id:"+transactional_id;
        res += ", contract_id:"+contract_id;
        res += ", required:"+required;
        res += ", origin:"+origin;
        res += ", signed_by:[";
        for (int i = 0; i < signed_by.size(); ++i) {
            if (i > 0)
                res += ", ";
            Role r = signed_by.get(i);
            res += r.getName() + ":" + Bytes.toHex(r.getKeys().iterator().next().fingerprint()).substring(0, 8) + "...";
        }
        res += "]";
        res += "}";
        return res;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.name = data.getString("name", null);

        this.type = data.getInt("type", null);

        this.transactional_id = data.getString("transactional_id", "");

        String str_contract_id = data.getString("contract_id", null);
        if (str_contract_id != null)
            this.contract_id = HashId.withDigest(Bytes.fromHex(str_contract_id).getData());
        else
            this.contract_id = null;

        this.required = data.getBoolean("required", true);

        String str_origin = data.getString("origin", null);
        if (str_origin != null)
            this.origin = HashId.withDigest(Bytes.fromHex(str_origin).getData());
        else
            this.origin = null;

        this.signed_by = deserializer.deserializeCollection(data.getList("signed_by", new ArrayList<>()));
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder data = new Binder();
        data.set("name", s.serialize(this.name));
        data.set("type", s.serialize(this.type));
        data.set("transactional_id", s.serialize(this.transactional_id));
        if (this.contract_id != null)
            data.set("contract_id", s.serialize(Bytes.toHex(this.contract_id.getDigest())));
        data.set("required", s.serialize(this.required));
        if (this.origin != null)
            data.set("origin", s.serialize(Bytes.toHex(this.origin.getDigest())));
        data.set("signed_by", s.serialize(signed_by));
        return data;
    }

    public boolean equals(ReferenceModel a) {
//        if (!this.name.equals(a.name))
//            return false;
//
//        if (this.type != a.type)
//            return false;
//
//        if ((this.transactional_id != null) && (a.transactional_id != null)) {
//            if (!this.transactional_id.equals(a.transactional_id))
//                return false;
//        } else if ((this.transactional_id != null) || (a.transactional_id != null))
//            return false;
//
//        if ((this.contract_id != null) && (a.contract_id != null)) {
//            if (!this.contract_id.equals(a.contract_id))
//                return false;
//        } else if ((this.contract_id != null) || (a.contract_id != null))
//            return false;
//
//        if (this.required != a.required)
//            return false;
//
//        if ((this.origin != null) && (a.origin != null)) {
//            if (!this.origin.equals(a.origin))
//                return false;
//        } else if ((this.origin != null) || (a.origin != null))
//            return false;
//
//        return true;
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = a.serialize(new BiSerializer());
        return dataThis.equals(dataA);
    }

    static {
        DefaultBiMapper.registerClass(ReferenceModel.class);
    }
};
