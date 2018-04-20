package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.ZonedDateTime;

public class NMutableEnviroment extends NImmutableEnvironment implements MutableEnvironment {

    public NMutableEnviroment(Contract contract) {
        super(contract);
    }

    @Override
    public <T> MutableEnvironment set(String key, T value) {
        return (MutableEnvironment) put(key, value);
    }

    @Override
    public void rollback() {

    }

    @Override
    public @Nullable ContractStorageSubscription createStorageSubscription(@NonNull HashId contractId, @NonNull ZonedDateTime expiresAt) {
        return null;
    }

    @Override
    public @NonNull ContractStorageSubscription createStorageSubscription(byte[] packedTransaction, @NonNull ZonedDateTime expiresAt) {
        return null;
    }
}
