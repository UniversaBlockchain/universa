package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements {@link ImmutableEnvironment} interface for slot contract.
 */
public class SlotImmutableEnvironment extends Binder implements ImmutableEnvironment {

    // slot contract this environment belongs to
    protected SlotContract contract;
    protected ZonedDateTime createdAt;
    // set of subscriptions holds by slot contract
    protected Set<ContractStorageSubscription> storageSubscriptionsSet = new HashSet<>();

    /**
     * Restore SlotImmutableEnvironment
     * @param contract slot contract this environment belongs to
     */
    public SlotImmutableEnvironment(SlotContract contract) {
        this.contract = contract;
        createdAt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
    }

    /**
     * Restore SlotImmutableEnvironment
     * @param contract slot contract this environment belongs to
     * @param kvBinder map stored in the ledger
     */
    public SlotImmutableEnvironment(SlotContract contract, Binder kvBinder) {
        this(contract);

        if(kvBinder!= null) {
            for (String key : kvBinder.keySet()) {
                super.set(key, kvBinder.get(key));
            }
        }
    }

    /**
     * Restore SlotImmutableEnvironment
     * @param contract slot contract this environment belongs to
     * @param kvBinder map stored in the ledger
     * @param storageSubscriptionsSet subscriptions for this environment
     */
    public SlotImmutableEnvironment(SlotContract contract, Binder kvBinder, Set<ContractStorageSubscription> storageSubscriptionsSet) {
        this(contract, kvBinder);

        if(storageSubscriptionsSet != null) {
            this.storageSubscriptionsSet = storageSubscriptionsSet;
        } else {
            this.storageSubscriptionsSet = new HashSet<>();
        }
    }

    @Override
    public <T extends Object> T set(String key, T value) {
        return null;
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
