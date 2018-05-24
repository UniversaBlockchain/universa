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

    private Set<NContractStorageSubscription> subscriptionsToAdd = new HashSet<>();
    private Set<NContractStorageSubscription> subscriptionsToDestroy = new HashSet<>();
    private Set<NContractStorageSubscription> subscriptionsToSave = new HashSet<>();

    private Set<NNameRecord> nameRecordsToAdd = new HashSet<>();
    private Set<NNameRecord> nameRecordsToDestroy = new HashSet<>();
    private Set<NNameRecord> nameRecordsToSave = new HashSet<>();

    /**
     * Restore NMutableEnvironment
     * @param contract slot contract this environment belongs to
     */
    public NMutableEnvironment(NSmartContract contract, NameCache nameCache, Ledger ledger) {
        super(contract,ledger);
        setNameCache(nameCache);
    }


    /**
     * Restore NMutableEnvironment
     * @param contract slot contract this environment belongs to
     * @param kvBinder map stored in the ledger
     */
    public NMutableEnvironment(NSmartContract contract, Binder kvBinder,
                               Collection<ContractStorageSubscription> subscriptions,
                               Collection<NameRecord> nameRecords, NameCache nameCache, Ledger ledger) {
        super(contract, kvBinder,subscriptions,nameRecords,ledger);
        setNameCache(nameCache);

    }


    @Override
    public <T extends Object> T set(String key, T value) {
        return (T) kvStore.put(key, value);
    }

    @Override
    public void rollback() {

    }

    @Override
    public @Nullable ContractStorageSubscription createStorageSubscription(@NonNull HashId contractId, @NonNull ZonedDateTime expiresAt) {
        //TODO: is it really needed?
        return null;
    }

    @Override
    public @NonNull ContractStorageSubscription createStorageSubscription(byte[] packedTransaction, @NonNull ZonedDateTime expiresAt) {
        NContractStorageSubscription sub = new NContractStorageSubscription(packedTransaction, expiresAt);
        subscriptionsToAdd.add(sub);
        return sub;
    }

    @Override
    public void setSubscriptionExpiresAt(ContractStorageSubscription subscription, ZonedDateTime expiresAt) {
        NContractStorageSubscription sub = (NContractStorageSubscription) subscription;
        sub.setExpiresAt(expiresAt);
        //existing subscription
        if(sub.getId() != 0) {
            subscriptionsToSave.add((NContractStorageSubscription) subscription);
        }
    }

    @Override
    public void destroySubscription(ContractStorageSubscription subscription) {
        subscriptionsToDestroy.add((NContractStorageSubscription) subscription);
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
        System.out.println("WWWW " + contract.getRevision());

        ledger.updateEnvironment(getId(),contract.getExtendedType(),contract.getId(), Boss.pack(kvStore),contract.getPackedTransaction());

        subscriptionsToDestroy.forEach(sub -> ledger.removeEnvironmentSubscription(sub.getId()));

        subscriptionsToSave.forEach(sub-> {
            ledger.updateSubscriptionInStorage(sub.getId(),sub.expiresAt());
        });

        subscriptionsToAdd.forEach(sub -> {
                    long storageId = ledger.saveContractInStorage(sub.getContract().getId(), sub.getPackedContract(), sub.getContract().getExpiresAt(), sub.getContract().getOrigin());
                    sub.setContractStorageId(storageId);
                    long subId = ledger.saveSubscriptionInStorage(storageId, sub.expiresAt());
                    sub.setId(subId);
                    ledger.saveEnvironmentSubscription(subId, getId());
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

    }
}
