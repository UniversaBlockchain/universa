package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;

import java.time.ZonedDateTime;

public class SlotContractStorageSubscription implements ContractStorageSubscription {

    private long id = 0;
    private long contractStorageId = 0;
    private ZonedDateTime expiresAt_ = ZonedDateTime.now().plusMonths(1);
    private boolean isReceiveEvents = false;

    private Contract trackingContract;

    public SlotContractStorageSubscription() {
        this.trackingContract = null;
    }

    public SlotContractStorageSubscription(Contract trackingContract) {
        this.trackingContract = trackingContract;
    }

    @Override
    public void destroy() {

    }

    @Override
    public void receiveEvents(boolean doReceive) {
        isReceiveEvents = doReceive;
    }

    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt_;
    }
    @Override
    public void setExpiresAt(ZonedDateTime expiresAt) {
        expiresAt_ = expiresAt;
    }

    public void setId(long value) {
        id = value;
    }
    public long getId() {
        return id;
    }

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
        return trackingContract.getPackedTransaction();
    }


    public boolean isReceiveEvents() {
        return isReceiveEvents;
    }
}
