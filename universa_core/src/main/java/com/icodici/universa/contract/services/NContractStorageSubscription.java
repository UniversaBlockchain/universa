package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;

import java.time.ZonedDateTime;

public class NContractStorageSubscription implements ContractStorageSubscription {
    @Override
    public ZonedDateTime expiresAt() {
        return null;
    }

    @Override
    public void setExpiresAt(ZonedDateTime expiresAt) {

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
