package com.icodici.universa.contract.services;

import com.icodici.crypto.digest.Sha256;
import com.icodici.universa.HashId;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node2.NameCache;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Implements {@link MutableEnvironment} interface for slot contract.
 */
public class NMutableEnvironment extends NImmutableEnvironment implements MutableEnvironment {

    private final NImmutableEnvironment immutable;
    private Set<NContractStorageSubscription> subscriptionsToAdd = new HashSet<>();
    private Set<NContractStorageSubscription> subscriptionsToDestroy = new HashSet<>();
    private Set<NContractStorageSubscription> subscriptionsToSave = new HashSet<>();

    private Set<NNameRecord> nameRecordsToAdd = new HashSet<>();
    private Set<NNameRecord> nameRecordsToDestroy = new HashSet<>();
    private Set<NNameRecord> nameRecordsToSave = new HashSet<>();

    private Set<NContractFollowerSubscription> subscriptionsFollowerToAdd = new HashSet<>();
    private Set<NContractFollowerSubscription> subscriptionsFollowerToDestroy = new HashSet<>();
    private Set<NContractFollowerSubscription> subscriptionsFollowerToSave = new HashSet<>();

    public NMutableEnvironment(NImmutableEnvironment ime) {
        super(ime.contract, ime.kvStore,ime.storageSubscriptionsSet,ime.nameRecordsSet,ime.ledger);
        setNameCache(ime.nameCache);
        setId(ime.getId());
        this.immutable = ime;
    }


    @Override
    public <T extends Object> T set(String key, T value) {
        return (T) kvStore.put(key, value);
    }

    @Override
    public void rollback() {

    }

    @Override
    public @Nullable ContractSubscription createFollowerSubscription(@NonNull HashId origin, @NonNull ZonedDateTime expiresAt) {
        NContractFollowerSubscription sub = new NContractFollowerSubscription(origin, expiresAt);
        subscriptionsFollowerToAdd.add(sub);
        return sub;
    }

    @Override
    public @NonNull ContractSubscription createStorageSubscription(byte[] packedTransaction, @NonNull ZonedDateTime expiresAt) {
        NContractStorageSubscription sub = new NContractStorageSubscription(packedTransaction, expiresAt);
        subscriptionsToAdd.add(sub);
        return sub;
    }

    @Override
    public void setSubscriptionExpiresAt(ContractSubscription subscription, ZonedDateTime expiresAt) {
        if (subscription instanceof NContractStorageSubscription) {
            NContractStorageSubscription sub = (NContractStorageSubscription) subscription;
            sub.setExpiresAt(expiresAt);

            //existing subscription
            if(sub.getId() != 0)
                subscriptionsToSave.add(sub);
        } else if (subscription instanceof NContractFollowerSubscription) {
            NContractFollowerSubscription sub = (NContractFollowerSubscription) subscription;
            sub.setExpiresAt(expiresAt);

            //existing subscription
            if(sub.getId() != 0)
                subscriptionsFollowerToSave.add(sub);
        }
    }

    @Override
    public void destroySubscription(ContractSubscription subscription) {
        if (subscription instanceof NContractStorageSubscription)
            subscriptionsToDestroy.add((NContractStorageSubscription) subscription);
        else if (subscription instanceof NContractFollowerSubscription)
            subscriptionsFollowerToDestroy.add((NContractFollowerSubscription) subscription);
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

    public void save() {

        ledger.updateEnvironment(getId(),contract.getExtendedType(),contract.getId(), Boss.pack(kvStore),contract.getPackedTransaction());

        subscriptionsToDestroy.forEach(sub -> ledger.removeEnvironmentSubscription(sub.getId()));
        subscriptionsFollowerToDestroy.forEach(sub -> ledger.removeEnvironmentFollowerSubscription(sub.getId()));

        subscriptionsToSave.forEach(sub-> {
            ledger.updateSubscriptionInStorage(sub.getId(), sub.expiresAt());
        });
        subscriptionsFollowerToSave.forEach(sub-> {
            ledger.updateFollowerSubscriptionInStorage(sub.getId(), sub.expiresAt(), sub.getCallbacks());
        });

        subscriptionsToAdd.forEach(sub -> {
                    long storageId = ledger.saveContractInStorage(sub.getContract().getId(), sub.getPackedContract(), sub.getContract().getExpiresAt(), sub.getContract().getOrigin());
                    sub.setContractStorageId(storageId);
                    long subId = ledger.saveSubscriptionInStorage(storageId, sub.expiresAt(), getId());
                    sub.setId(subId);
                }
        );
        subscriptionsFollowerToAdd.forEach(sub -> {
                    long subId = ledger.saveFollowerSubscriptionInStorage(sub.getOrigin(), sub.expiresAt(), sub.mutedAt(), getId());
                    sub.setId(subId);
                }
        );

        ledger.clearExpiredStorageContracts();

        nameRecordsToDestroy.forEach(nameRecord -> ledger.removeNameRecord(nameRecord.getNameReduced()));


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

        immutable.storageSubscriptionsSet.removeAll(subscriptionsToDestroy);
        immutable.storageSubscriptionsSet.addAll(subscriptionsToAdd);

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
