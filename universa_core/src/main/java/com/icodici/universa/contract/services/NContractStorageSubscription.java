package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;

import java.io.IOException;
import java.time.ZonedDateTime;

/**
 * Implements {@link ContractStorageSubscription} interface for slot contract.
 */
public class NContractStorageSubscription implements ContractStorageSubscription {

    private long id = 0;

    private byte[] packedContract;
    private long contractStorageId = 0;
    private long environmentId = 0;
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusMonths(1);
    private boolean isReceiveEvents = false;

    private Contract trackingContract;

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
}
