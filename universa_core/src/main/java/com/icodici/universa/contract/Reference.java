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

public class Reference implements BiSerializable {
    public String name = "";
    public int type = TYPE_EXISTING;
    public String transactional_id = "";
    public HashId contract_id = null;
    public boolean required = true;
    public HashId origin = null;
    public String origin_bak = null;
    public List<Role> signed_by = new ArrayList<>();
    public List<String> fields = new ArrayList<>();
    public List<String> roles = new ArrayList<>();

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


        this.origin_bak = data.getString("origin_bak", null);

        List<String> roles = data.getList("roles", null);
        if (roles != null) {
            this.roles.clear();
            roles.forEach(this::addRole);
        }

        List<String> fields = data.getList("fields", null);
        if (fields != null) {
            this.fields.clear();
            fields.forEach(this::addField);
        }

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

        data.set("origin_bak", s.serialize(this.origin_bak));
        data.set("roles", s.serialize(this.roles));
        data.set("fields", s.serialize(this.fields));

        return data;
    }

    public boolean equals(Reference a) {
        Binder dataThis = serialize(new BiSerializer());
        Binder dataA = a.serialize(new BiSerializer());
        return dataThis.equals(dataA);
    }






    public String getName() {
        return name;
    }

    public Reference setName(String name) {
        this.name = name;
        return this;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Reference addRole(String role) {
        this.roles.add(role);
        return this;
    }

    public Reference setRoles(List<String> roles) {
        this.roles = roles;
        return this;
    }

    public String getOrigin() {
        return origin_bak;
    }

    public Reference setOrigin(String origin) {
        this.origin_bak = origin;
        return this;
    }

    public List<String> getFields() {
        return fields;
    }

    public Reference addField(String field) {
        this.fields.add(field);
        return this;
    }

    public Reference setFields(List<String> fields) {
        this.fields = fields;
        return this;
    }





    static {
        DefaultBiMapper.registerClass(Reference.class);
    }
};
