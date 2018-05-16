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
    private String unsName;
    private String unsDescription;
    private String unsURL;
    private List<UnsRecord> unsRecords = new ArrayList<>();

    public static final String NAME_FIELD_NAME = "name";
    public static final String DESCRIPTION_FIELD_NAME = "description";
    public static final String URL_FIELD_NAME = "url";
    public static final String ENTRIES_FIELD_NAME = "entries";

    public UnsName() {}

    public UnsName(String name, String description, String URL) {
        unsName = name;
        unsDescription = description;
        unsURL = URL;
    }

    public UnsName(String name, String description, String URL, UnsRecord record) {
        unsName = name;
        unsDescription = description;
        unsURL = URL;
        unsRecords.add(record);
    }

    public UnsName(String name, String description, String URL, List<UnsRecord> records) {
        unsName = name;
        unsDescription = description;
        unsURL = URL;
        unsRecords.addAll(records);
    }

    protected UnsName initializeWithDsl(Binder binder) {
        unsName = binder.getString(NAME_FIELD_NAME, null);
        unsDescription = binder.getString(DESCRIPTION_FIELD_NAME, null);
        unsURL = binder.getString(URL_FIELD_NAME, null);

        ArrayList<?> entries = binder.getArray(ENTRIES_FIELD_NAME);
        for (Object entry: entries) {
            Binder b;
            if (entry.getClass().getName().endsWith("Binder"))
                b = (Binder) entry;
            else
                b = new Binder((Map) entry);

            UnsRecord unsRecord = new UnsRecord().initializeWithDsl(b);
            unsRecords.add(unsRecord);
        }

        return this;
    }

    public String getUnsName() { return unsName; }
    public String getUnsDescription() { return unsDescription; }
    public String getUnsURL() { return unsURL; }

    public void setUnsName(String name) { unsName = name; }
    public void setUnsDescription(String description) { unsDescription = description; }
    public void setUnsURL(String URL) { unsURL = URL; }

    public int getRecordsCount() { return unsRecords.size(); }

    public void addUnsRecord(UnsRecord record) {
        unsRecords.add(record);
    }

    public void addUnsRecord(List<UnsRecord> records) {
        unsRecords.addAll(records);
    }

    public UnsRecord getUnsRecord(int index) {
        if ((index < 0) || index >= unsRecords.size())
            return null;
        else
            return unsRecords.get(index);
    }

    public int findUnsRecordByAddress(KeyAddress address) {
        for (int i = 0; i < unsRecords.size(); i++)
            if (unsRecords.get(i).isMatchingAddress(address))
                return i;

        return -1;
    }

    public int findUnsRecordByKey(AbstractKey key) {
        for (int i = 0; i < unsRecords.size(); i++)
            if (unsRecords.get(i).isMatchingKey(key))
                return i;

        return -1;
    }

    public int findUnsRecordByOrigin(HashId origin) {
        for (int i = 0; i < unsRecords.size(); i++)
            if (unsRecords.get(i).isMatchingOrigin(origin))
                return i;

        return -1;
    }

    public void removeUnsRecord(int index) {
        if ((index < 0) || index >= unsRecords.size())
            throw new IllegalArgumentException("Index of removing record is outbound");
        else
            unsRecords.remove(index);
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.set("unsName", serializer.serialize(unsName));
        data.set("unsDescription", serializer.serialize(unsDescription));
        data.set("unsURL", serializer.serialize(unsURL));
        data.set("unsRecords", serializer.serialize(unsRecords));

        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.unsName = data.getString("unsName");
        this.unsDescription = data.getString("unsDescription");
        this.unsURL = data.getString("unsURL");
        this.unsRecords = deserializer.deserialize(data.getList("unsRecords", null));
    }

    public List<UnsRecord> getUnsRecords() {
        return unsRecords;
    }

    static {
        Config.forceInit(UnsName.class);
        Config.forceInit(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsName.class);
    }
}
