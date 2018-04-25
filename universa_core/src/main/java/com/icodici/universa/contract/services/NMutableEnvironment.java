package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.NodeContract;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.ZonedDateTime;

public class NMutableEnvironment extends NImmutableEnvironment implements MutableEnvironment {

    public NMutableEnvironment(Contract contract) {
        super(contract);
    }

    public NMutableEnvironment(Contract contract, Binder kvBinder) {
        super(contract, kvBinder);
    }

//    @Override
//    public <T> MutableEnvironment set(String key, T value) {
//        return (MutableEnvironment) put(key, value);
//    }

    @Override
    public void rollback() {

    }

    @Override
    public @Nullable ContractStorageSubscription createStorageSubscription(@NonNull HashId contractId, @NonNull ZonedDateTime expiresAt) {
        ContractStorageSubscription css = new NContractStorageSubscription();
        storageSubscriptionsSet.add(css);
        return css;
    }

    @Override
    public @NonNull ContractStorageSubscription createStorageSubscription(byte[] packedTransaction, @NonNull ZonedDateTime expiresAt) {
        ContractStorageSubscription css = new NContractStorageSubscription();
        storageSubscriptionsSet.add(css);
        return css;
    }
}
