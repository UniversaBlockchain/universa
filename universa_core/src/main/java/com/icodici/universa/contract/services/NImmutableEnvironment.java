package com.icodici.universa.contract.services;

import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node.models.NameRecordModel;
import com.icodici.universa.node2.NameCache;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements {@link ImmutableEnvironment} interface for slot contract.
 */
public class NImmutableEnvironment extends Binder implements ImmutableEnvironment {

    private long id = 0;
    protected NameCache nameCache;
    protected Ledger ledger;
    // slot contract this environment belongs to
    protected NSmartContract contract;
    protected ZonedDateTime createdAt;
    // set of subscriptions holds by slot contract
    protected Set<ContractStorageSubscription> storageSubscriptionsSet = new HashSet<>();
    protected Set<NameRecord> nameRecordsSet = new HashSet<>();


    public void setNameCache(NameCache nameCache) {
        this.nameCache = nameCache;
    }


    /**
     * Restore NImmutableEnvironment
     * @param contract slot contract this environment belongs to
     */
    public NImmutableEnvironment(NSmartContract contract, Ledger ledger) {
        this.contract = contract;
        this.ledger = ledger;
        createdAt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
    }

    /**
     * Restore NImmutableEnvironment
     * @param contract slot contract this environment belongs to
     * @param kvBinder map stored in the ledger
     */
    public NImmutableEnvironment(NSmartContract contract, Binder kvBinder,
                                 Collection<ContractStorageSubscription> subscriptions,
                                 Collection<NameRecord> nameRecords, Ledger ledger) {
        this(contract,ledger);

        if(kvBinder!= null) {
            for (String key : kvBinder.keySet()) {
                super.set(key, kvBinder.get(key));
            }
        }

        storageSubscriptionsSet.addAll(subscriptions);
        nameRecordsSet.addAll(nameRecords);
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

    @Override
    public Iterable<NameRecord> nameRecords() {
        return nameRecordsSet;
    }

    @Override
    public boolean tryAllocate(Collection<String> reducedNamesToAllocate, Collection<HashId> originsToAllocate, Collection<String> addressesToAllocate) {
        boolean checkResultNames;
        boolean checkResult = checkResultNames = isNamesAvailable(reducedNamesToAllocate);

        boolean checkResultOrigins = false;
        if (checkResult)
            checkResult = checkResultOrigins = isOriginsAvailable(originsToAllocate);

        boolean checkResultAddresses = false;
        if (checkResult)
            checkResult = checkResultAddresses = isAddressesAvailable(addressesToAllocate);

        if (!checkResult) {
            if (checkResultNames)
                nameCache.unlockNameList(reducedNamesToAllocate);
            if (checkResultOrigins)
                nameCache.unlockOriginList(originsToAllocate);
            if (checkResultAddresses)
                nameCache.unlockAddressList(addressesToAllocate);
        }

        return checkResult;
    }

    private boolean isNamesAvailable(Collection<String> reducedNames) {
        boolean checkResult;

        if (reducedNames.size() == 0)
            return true;

        checkResult = nameCache.lockNameList(reducedNames);
        if (!checkResult) {
            return checkResult;
        }

        checkResult = ledger.isAllNameRecordsAvailable(reducedNames);
        if (!checkResult) {
            nameCache.unlockNameList(reducedNames);
            return checkResult;
        }

        return checkResult;
    }

    private boolean isOriginsAvailable(Collection<HashId> origins) {
        boolean checkResult;

        if (origins.size() == 0)
            return true;

        checkResult = nameCache.lockOriginList(origins);
        if (!checkResult) {
            return checkResult;
        }

        checkResult = ledger.isAllOriginsAvailable(origins);
        if (!checkResult) {
            nameCache.unlockOriginList(origins);
            return checkResult;
        }

        return checkResult;
    }

    private boolean isAddressesAvailable(Collection<String> addresses) {
        boolean checkResult;

        if (addresses.size() == 0)
            return true;

        checkResult = nameCache.lockAddressList(addresses);
        if (!checkResult) {
            return checkResult;
        }

        checkResult = ledger.isAllAddressesAvailable(addresses);
        if (!checkResult) {
            nameCache.unlockAddressList(addresses);
            return checkResult;
        }

        return checkResult;
    }

    public NMutableEnvironment getMutable() {
        return new NMutableEnvironment(contract,this, storageSubscriptionsSet,nameRecordsSet,nameCache,ledger);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
