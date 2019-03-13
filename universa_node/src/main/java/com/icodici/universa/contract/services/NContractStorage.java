package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.time.ZonedDateTime;

/**
 * Implements {@link ContractStorage} interface for contract.
 */
public class NContractStorage implements ContractStorage, BiSerializable {

    private long id = 0;
    private byte[] packedContract;
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusMonths(1);

    private Contract trackingContract;

    public NContractStorage() {}

    public NContractStorage(byte[] packedContract, ZonedDateTime expiresAt) {
        this.packedContract = packedContract;
        this.expiresAt = expiresAt;
        try {
            this.trackingContract = Contract.fromPackedTransaction(packedContract);
        } catch (IOException e) {
            throw new IllegalArgumentException("NContractStorage unable to unpack TP " + e.getMessage());
        }
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

    @Override
    public Contract getContract() {
        return trackingContract;
    }
    @Override
    public byte[] getPackedContract() {
        return packedContract;
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
