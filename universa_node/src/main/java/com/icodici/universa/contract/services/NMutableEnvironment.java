package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Implements {@link MutableEnvironment} interface for smart contracts.
 */
public class NMutableEnvironment extends NImmutableEnvironment implements MutableEnvironment {

    private final NImmutableEnvironment immutable;
    private Set<NContractSubscription> subscriptionsToAdd = new HashSet<>();
    private Set<NContractSubscription> subscriptionsToDestroy = new HashSet<>();
    private Set<NContractSubscription> subscriptionsToSave = new HashSet<>();

    private Set<NNameRecord> nameRecordsToAdd = new HashSet<>();
    private Set<NNameRecord> nameRecordsToDestroy = new HashSet<>();
    private Set<NNameRecord> nameRecordsToSave = new HashSet<>();

    private Set<NContractStorage> storagesToAdd = new HashSet<>();
    private Set<NContractStorage> storagesToDestroy = new HashSet<>();
    private Set<NContractStorage> storagesToSave = new HashSet<>();

    public NMutableEnvironment(NImmutableEnvironment ime) {
        super(ime.contract, ime.kvStore, ime.subscriptionsSet, ime.storagesSet, ime.nameRecordsSet, ime.followerService, ime.ledger);
        setNameCache(ime.nameCache);
        setId(ime.getId());
        immutable = ime;
    }


    @Override
    public <T extends Object> T set(String key, T value) {
        return (T) kvStore.put(key, value);
    }

    @Override
    public void rollback() {

    }

    @Override
    public @NonNull ContractSubscription createChainSubscription(@NonNull HashId origin, @NonNull ZonedDateTime expiresAt) {
        NContractSubscription sub = new NContractSubscription(origin, true, expiresAt);
        subscriptionsToAdd.add(sub);
        return sub;
    }

    @Override
    public @NonNull ContractSubscription createContractSubscription(@NonNull HashId id, @NonNull ZonedDateTime expiresAt) {
        NContractSubscription sub = new NContractSubscription(id, false, expiresAt);
        subscriptionsToAdd.add(sub);
        return sub;
    }

    @Override
    public @NonNull NContractStorage createContractStorage(byte[] packedTransaction, @NonNull ZonedDateTime expiresAt) {
        NContractStorage storage = new NContractStorage(packedTransaction, expiresAt);
        storagesToAdd.add(storage);
        return storage;
    }

    @Override
    public void setSubscriptionExpiresAt(ContractSubscription subscription, ZonedDateTime expiresAt) {
        NContractSubscription sub = (NContractSubscription) subscription;
        sub.setExpiresAt(expiresAt);

        //existing subscription
        if(sub.getId() != 0)
            subscriptionsToSave.add(sub);
    }

    @Override
    public void destroySubscription(ContractSubscription subscription) {
        subscriptionsToDestroy.add((NContractSubscription) subscription);
    }

    @Override
    public void setStorageExpiresAt(ContractStorage contractStorage, ZonedDateTime expiresAt) {
        NContractStorage storage = (NContractStorage) contractStorage;
        storage.setExpiresAt(expiresAt);

        //existing storage
        if(storage.getId() != 0)
            storagesToSave.add(storage);
    }

    @Override
    public void destroyStorage(ContractStorage contractStorage) {
        storagesToDestroy.add((NContractStorage) contractStorage);
    }

    @Override
    public @NonNull NameRecord createNameRecord(@NonNull UnsName unsName, @NonNull ZonedDateTime expiresAt) {
        NNameRecord nr = new NNameRecord(unsName,expiresAt);
        nr.setEnvironmentId(this.getId());
        nameRecordsToAdd.add(nr);
        return nr;
    }


    @Override
    public void setNameRecordExpiresAt(NameRecord nameRecord, ZonedDateTime expiresAt) {
        NNameRecord nnr = (NNameRecord) nameRecord;
        nnr.setExpiresAt(expiresAt);
        if(nnr.getId() != 0) {
            nameRecordsToSave.add(nnr);
        }
    }

    @Override
    public void destroyNameRecord(NameRecord nameRecord) {
        nameRecordsToDestroy.add((NNameRecord) nameRecord);
    }

    @Override
    public void save() {

        ledger.updateEnvironment(getId(),contract.getExtendedType(),contract.getId(), Boss.pack(kvStore),contract.getPackedTransaction());

        subscriptionsToDestroy.forEach(sub -> ledger.removeEnvironmentSubscription(sub.getId()));

        subscriptionsToSave.forEach(sub-> ledger.updateSubscriptionInStorage(sub.getId(), sub.expiresAt()));

        subscriptionsToAdd.forEach(sub -> {
                    long subId = ledger.saveSubscriptionInStorage(sub.getHashId(), sub.isChainSubscription(), sub.expiresAt(), getId());
                    sub.setId(subId);
                }
        );

        storagesToDestroy.forEach(storage -> ledger.removeEnvironmentStorage(storage.getId()));

        storagesToSave.forEach(storage-> ledger.updateStorageExpiresAt(storage.getId(), storage.expiresAt()));

        storagesToAdd.forEach(storage -> {
                    long storageId = ledger.saveContractInStorage(storage.getContract().getId(), storage.getPackedContract(),
                            storage.expiresAt(), storage.getContract().getOrigin(), getId());
                    storage.setId(storageId);
                }
        );

        nameRecordsToDestroy.forEach(nameRecord -> ledger.removeNameRecord(nameRecord.getNameReduced()));

        ledger.clearExpiredStorageContractBinaries();

        if (followerService != null)
            followerService.save();

        List<String> addressList = new LinkedList<>();
        List<String> nameList = new LinkedList<>();
        List<HashId> originsList = new LinkedList<>();


        nameRecordsToSave.forEach(nameRecord -> {
            ledger.updateNameRecord(nameRecord.getId(),nameRecord.expiresAt());
            nameList.add(nameRecord.getNameReduced());
            nameRecord.getEntries().forEach( e -> {
                if(e.getOrigin() != null) {
                    originsList.add(e.getOrigin());
                }

                if(e.getLongAddress() != null) {
                    addressList.add(e.getLongAddress());
                }

                if(e.getShortAddress() != null) {
                    addressList.add(e.getShortAddress());
                }

            });
        });
        nameRecordsToAdd.forEach(nameRecord -> {
            ledger.addNameRecord(nameRecord);
            nameList.add(nameRecord.getNameReduced());
            nameRecord.getEntries().forEach( e -> {
                if(e.getOrigin() != null) {
                    originsList.add(e.getOrigin());
                }

                if(e.getLongAddress() != null) {
                    addressList.add(e.getLongAddress());
                }

                if(e.getShortAddress() != null) {
                    addressList.add(e.getShortAddress());
                }

            });
        });


        nameCache.unlockAddressList(addressList);
        nameCache.unlockNameList(nameList);
        nameCache.unlockOriginList(originsList);

        immutable.subscriptionsSet.removeAll(subscriptionsToDestroy);
        immutable.subscriptionsSet.addAll(subscriptionsToAdd);

        immutable.storagesSet.removeAll(storagesToDestroy);
        immutable.storagesSet.addAll(storagesToAdd);

        immutable.nameRecordsSet.removeAll(nameRecordsToDestroy);
        immutable.nameRecordsSet.addAll(nameRecordsToAdd);

        immutable.kvStore.clear();
        for (String key : kvStore.keySet()) {
            immutable.kvStore.set(key, kvStore.get(key));
        }
    }

    public Binder getKVStore() {
        return kvStore;
    }
}
