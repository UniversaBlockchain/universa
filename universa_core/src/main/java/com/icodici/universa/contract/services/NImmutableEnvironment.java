package com.icodici.universa.contract.services;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.SmartContract;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class NImmutableEnvironment extends Binder implements ImmutableEnvironment {

    protected Contract contract;
    protected ZonedDateTime createdAt;
    protected Set<ContractStorageSubscription> storageSubscriptionsSet = new HashSet<>();

    public NImmutableEnvironment(Contract contract) {
        this.contract = contract;
        createdAt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
    }

    public NImmutableEnvironment(Contract contract, Binder kvBinder) {
        this(contract);

        for(String key : kvBinder.keySet()) {
            set(key, kvBinder.get(key));
        }
    }

    @Override
    public <T extends Contract> @NonNull T getContract() {
        return (T) contract;
    }

    @Override
    public <T, U extends T> T get(String keyName, U defaultValue) {
        if(this.containsKey(keyName))
            return (T) this.get(keyName);

        return defaultValue;
    }

    @Override
    public @NonNull ZonedDateTime instanceCreatedAt() {
        return createdAt;
    }

    @Override
    public Iterable<ContractStorageSubscription> storageSubscriptions() {
        return storageSubscriptionsSet;
    }
}
