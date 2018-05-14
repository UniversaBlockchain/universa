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

    public static final String ADDRESSES_FIELD_NAME = "addresses";
    public static final String ORIGIN_FIELD_NAME = "origin";

    public UnsRecord() {}

    public UnsRecord(HashId origin) {
        unsOrigin = origin;
    }

    public UnsRecord(KeyAddress address) {
        unsAddresses.add(address);
    }

    public UnsRecord(List<KeyAddress> addresses) {
        if (addresses.size() > 2)
            throw new IllegalArgumentException("Addresses list should not be contains more 2 addresses");

        if ((addresses.size() == 2) &&
                ((!addresses.get(0).isLong() && !addresses.get(1).isLong()) ||
                        (addresses.get(0).isLong() && addresses.get(1).isLong())))
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
        if (digest != null) {
            unsOrigin = HashId.withDigest(digest);
        } else {
            ArrayList<?> list = binder.getArray(ADDRESSES_FIELD_NAME);
            for (Object obj: list)
                if (obj.getClass().getName().endsWith("String")) {
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

        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        this.unsAddresses = deserializer.deserialize(data.getList("addresses", new ArrayList<>()));
        Object originObj = data.get("origin");
        if (originObj != null)
            this.unsOrigin = deserializer.deserialize(originObj);
    }

    static {
        Config.forceInit(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsRecord.class);
    }
}
