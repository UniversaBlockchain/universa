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

public class UnsRecord implements BiSerializable {
    private List<KeyAddress> unsAddresses = new ArrayList<>();
    private HashId unsOrigin;
    private Binder unsData;

    public static final String ADDRESSES_FIELD_NAME = "addresses";
    public static final String ORIGIN_FIELD_NAME = "origin";
    public static final String DATA_FIELD_NAME = "data";

    public UnsRecord() {}

    public UnsRecord(HashId origin) {
        unsOrigin = origin;
    }

    public UnsRecord(Binder data) {
        unsData = data;
    }

    public UnsRecord(KeyAddress address) {
        unsAddresses.add(address);
    }

    public UnsRecord(List<KeyAddress> addresses) {
        if (addresses.size() > 2)
            throw new IllegalArgumentException("Addresses list should not be contains more 2 addresses");

        if ((addresses.size() == 2) && addresses.get(0).isLong() == addresses.get(1).isLong())
            throw new IllegalArgumentException("Addresses list may be contains only one short and one long addresses");

        unsAddresses.addAll(addresses);
    }

    public UnsRecord(AbstractKey key, int keyMark) {
        unsAddresses.add(new KeyAddress(key, keyMark, false));
        unsAddresses.add(new KeyAddress(key, keyMark, true));
    }

    public UnsRecord(AbstractKey key) {
        unsAddresses.add(new KeyAddress(key, 0, false));
        unsAddresses.add(new KeyAddress(key, 0, true));
    }

    public HashId getOrigin() { return unsOrigin; }
    public Binder getData() { return unsData; }

    public List<KeyAddress> getAddresses() { return unsAddresses; }

    public boolean isMatchingAddress(KeyAddress address) {
        for (KeyAddress addr: unsAddresses)
            if (addr.isMatchingKeyAddress(address))
                return true;

        return false;
    }

    public boolean isMatchingKey(AbstractKey key) {
        for (KeyAddress addr: unsAddresses)
            if (addr.isMatchingKey(key))
                return true;

        return false;
    }

    public boolean isMatchingOrigin(HashId origin) {
        return origin.equals(unsOrigin);
    }

    protected UnsRecord initializeWithDsl(Binder binder) {
        String digest = binder.getString(ORIGIN_FIELD_NAME, null);
        Binder data = binder.getBinder(DATA_FIELD_NAME, null);
        if(data != null) {
            unsData = data;
        } else if (digest != null) {
            unsOrigin = HashId.withDigest(digest);
        } else {
            ArrayList<?> list = binder.getArray(ADDRESSES_FIELD_NAME);
            for (Object obj: list)
                if (obj instanceof String) {
                    try {
                        unsAddresses.add(new KeyAddress((String) obj));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Error converting address from base58 string: " + e.getMessage());
                    }
                }
        }

        return this;
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        if (unsAddresses.size() > 0)
            data.set("addresses", serializer.serialize(unsAddresses));

        if (unsOrigin != null)
            data.set("origin", serializer.serialize(unsOrigin));

        if (unsData != null)
            data.set("data", serializer.serialize(unsData));

        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.unsAddresses = deserializer.deserialize(data.getList("addresses", new ArrayList<>()));
        Object originObj = data.get("origin");
        if (originObj != null)
            this.unsOrigin = deserializer.deserialize(originObj);

        Object dataObj = data.get("data");
        if (dataObj != null)
            this.unsData = deserializer.deserialize(dataObj);
    }

    static {
        Config.forceInit(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsRecord.class);
    }


    public boolean equalsTo(NameRecordEntry entry) {
        String longAddress = null;
        String shortAddress = null;
        for(KeyAddress keyAddress : getAddresses()) {
            if(keyAddress.isLong())
                longAddress = keyAddress.toString();
            else
                shortAddress = keyAddress.toString();
        }


        boolean checkResult;
        if(unsOrigin != null && entry.getOrigin() != null) {
            checkResult = unsOrigin.equals(entry.getOrigin());
        } else {
            checkResult = unsOrigin == null && entry.getOrigin() == null;
        }

        if(!checkResult)
            return false;


        if(unsData != null && entry.getData() != null) {
            checkResult = unsData.equals(entry.getData());
        } else {
            checkResult = unsData == null && entry.getData() == null;
        }

        if(!checkResult)
            return false;


        if(longAddress != null && entry.getLongAddress() != null) {
            checkResult = longAddress.equals(entry.getLongAddress());
        } else {
            checkResult = longAddress == null && entry.getLongAddress() == null;
        }

        if(!checkResult)
            return false;


        if(shortAddress != null && entry.getShortAddress() != null) {
            checkResult = shortAddress.equals(entry.getShortAddress());
        } else {
            checkResult = shortAddress == null && entry.getShortAddress() == null;
        }

        return checkResult;
    }


    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof UnsRecord))
            return false;

        UnsRecord to = (UnsRecord) obj;

        if(to.unsOrigin != null) {
            if(unsOrigin == null)
                return false;

            if(!to.unsOrigin.equals(unsOrigin))
                return false;
        } else {
            if(unsOrigin != null)
                return false;
        }

        if(!getAddresses().equals(to.getAddresses())) {
            return false;
        }


        if(to.unsData != null) {
            if(unsData == null)
                return false;

            if(!to.unsData.equals(unsData))
                return false;
        } else {
            if(unsData != null)
                return false;
        }

        return true;
    }
}
