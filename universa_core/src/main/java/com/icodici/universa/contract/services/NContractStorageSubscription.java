package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;

import java.time.ZonedDateTime;

public class NContractStorageSubscription implements ContractStorageSubscription {

    private long id = 0;
    private ZonedDateTime expiresAt_ = ZonedDateTime.now().plusMonths(1);

    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt_;
    }

    @Override
    public void setExpiresAt(ZonedDateTime expiresAt) {

    }

    public void setId(long value) {
        id = value;
    }

    public long getId() {
        return id;
    }

    @Override
    public void destroy() {

    }

    @Override
    public Contract getContract() {
        return null;
    }

    @Override
    public byte[] getPackedContract() {
        return new byte[0];
    }

    @Override
    public void receiveEvents(boolean doReceive) {

    }
}
