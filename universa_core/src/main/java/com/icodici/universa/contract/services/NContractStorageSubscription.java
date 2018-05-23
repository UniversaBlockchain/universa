package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.time.ZonedDateTime;

/**
 * Implements {@link ContractStorageSubscription} interface for slot contract.
 */
public class NContractStorageSubscription implements ContractStorageSubscription,BiSerializable {

    private long id = 0;

    private byte[] packedContract;
    private long contractStorageId = 0;
    private long environmentId = 0;
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusMonths(1);
    private boolean isReceiveEvents = false;

    private Contract trackingContract;

    public NContractStorageSubscription() {

    }

    public NContractStorageSubscription(byte[] packedContract, ZonedDateTime expiresAt) {
        this.packedContract = packedContract;
        this.expiresAt = expiresAt;
        try {
            this.trackingContract = Contract.fromPackedTransaction(packedContract);
        } catch (IOException e) {
            throw new IllegalArgumentException("NContractStorageSubscription unable to unpack TP " + e.getMessage());
        }
    }

    @Override
    public void receiveEvents(boolean doReceive) {
        isReceiveEvents = doReceive;
    }

    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void setId(long value) {
        id = value;
    }
    public long getId() {
        return id;
    }

    /**
     * Set id from ledger for record with stored contract.
     *
     * @param value is id
     */
    public void setContractStorageId(long value) {
        contractStorageId = value;
    }
    public long getContractStorageId() {
        return contractStorageId;
    }

    @Override
    public Contract getContract() {
        return trackingContract;
    }
    @Override
    public byte[] getPackedContract() {
        return packedContract;
    }


    public boolean isReceiveEvents() {
        return isReceiveEvents;
    }

    public long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(long environmentId) {
        this.environmentId = environmentId;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        packedContract = data.getBinary("packedContract");
        trackingContract = Contract.fromPackedTransaction(packedContract);
        expiresAt = data.getZonedDateTimeOrThrow("expiresAt");
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.put("packedContract",serializer.serialize(packedContract));
        data.put("expiresAt", serializer.serialize(expiresAt));
        return data;
    }
}
