package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.time.ZonedDateTime;

/**
 * Implements {@link ContractSubscription} interface for contract.
 */
public class NContractSubscription implements ContractSubscription, BiSerializable {

    private long id = 0;

    private HashId hashId;
    private boolean isChainSubscription;
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusMonths(1);

    public NContractSubscription() {}

    public NContractSubscription(HashId hashId, boolean isChainSubscription, ZonedDateTime expiresAt) {
        this.hashId = hashId;
        this.isChainSubscription = isChainSubscription;
        this.expiresAt = expiresAt;
    }

    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt;
    }

    @Override
    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setId(long value) {
        id = value;
    }
    public long getId() {
        return id;
    }

    @Override
    public HashId getHashId() { return hashId; }

    @Override
    public HashId getContractId() {
        if (!isChainSubscription)
            return hashId;
        else
            return null;
    }

    @Override
    public HashId getOrigin() {
        if (isChainSubscription)
            return hashId;
        else
            return null;
    }

    @Override
    public boolean isChainSubscription() { return isChainSubscription; }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        hashId = deserializer.deserialize(data.get("hashId"));
        isChainSubscription = data.getBooleanOrThrow("isChainSubscription");
        expiresAt = data.getZonedDateTimeOrThrow("expiresAt");
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.put("hashId", serializer.serialize(hashId));
        data.put("isChainSubscription", isChainSubscription);
        data.put("expiresAt", serializer.serialize(expiresAt));
        return data;
    }
}
