package com.icodici.universa.contract.services;

import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NameCache;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Implements {@link ImmutableEnvironment} interface for slot contract.
 */
public class NImmutableEnvironment implements ImmutableEnvironment, BiSerializable {

    private long id = 0;
    protected NameCache nameCache;
    protected Ledger ledger;
    // smart contract this environment belongs to
    protected NSmartContract contract;
    protected ZonedDateTime createdAt;

    // set of subscriptions holds by contract
    protected Set<ContractSubscription> subscriptionsSet = new HashSet<>();

    // set of storages holds by contract
    protected Set<ContractStorage> storagesSet = new HashSet<>();

    protected Set<NameRecord> nameRecordsSet = new HashSet<>();
    protected Binder kvStore = new Binder();

    protected FollowerService followerService;

    public void setNameCache(NameCache nameCache) {
        this.nameCache = nameCache;
    }

    public NImmutableEnvironment() {}

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
                                 Collection<ContractSubscription> subscriptions,
                                 Collection<ContractStorage> storages,
                                 Collection<NameRecord> nameRecords,
                                 FollowerService followerService, Ledger ledger) {
        this(contract, ledger);

        if(kvBinder!= null) {
            for (String key : kvBinder.keySet()) {
                kvStore.set(key, kvBinder.get(key));
            }
        }

        subscriptions.forEach(sub -> {
            if (sub instanceof NContractSubscription)
                subscriptionsSet.add(sub);
        });

        storagesSet.addAll(storages);

        nameRecordsSet.addAll(nameRecords);

        this.followerService = followerService;
    }


    @Override
    public <T extends Contract> @NonNull T getContract() {
        return (T) contract;
    }

    @Override
    public <T, U extends T> T get(String keyName, U defaultValue) {
        if(kvStore.containsKey(keyName))
            return (T) kvStore.get(keyName);

        return defaultValue;
    }

    @Override
    public @NonNull ZonedDateTime instanceCreatedAt() {
        return createdAt;
    }

    @Override
    public Iterable<ContractSubscription> subscriptions() {
        return subscriptionsSet;
    }

    @Override
    public Iterable<ContractStorage> storages() {
        return storagesSet;
    }

    @Override
    public Iterable<NameRecord> nameRecords() {
        return nameRecordsSet;
    }

    @Override
    public List<ErrorRecord> tryAllocate(Collection<String> reducedNamesToAllocate, Collection<HashId> originsToAllocate, Collection<String> addressesToAllocate) {
        List<String> namesErrors = isNamesAvailable(reducedNamesToAllocate);
        List<String> originsErrors = isOriginsAvailable(originsToAllocate);
        List<String> addressesErros = isAddressesAvailable(addressesToAllocate);
        boolean checkResult = namesErrors.isEmpty() && originsErrors.isEmpty() && addressesErros.isEmpty();

        if (!checkResult) {
            if (namesErrors.isEmpty())
                nameCache.unlockNameList(reducedNamesToAllocate);
            if (originsErrors.isEmpty())
                nameCache.unlockOriginList(originsToAllocate);
            if (addressesErros.isEmpty())
                nameCache.unlockAddressList(addressesToAllocate);
        }

        List<ErrorRecord> res = new ArrayList<>();
        for (String s : namesErrors)
            res.add(new ErrorRecord(Errors.FAILED_CHECK, "names", "name '"+s+"' is not available"));
        for (String s : originsErrors)
            res.add(new ErrorRecord(Errors.FAILED_CHECK, "origins", "origin '"+s+"' is not available"));
        for (String s : addressesErros)
            res.add(new ErrorRecord(Errors.FAILED_CHECK, "addresses", "address '"+s+"' is not available"));
        return res;
    }

    private List<String> isNamesAvailable(Collection<String> reducedNames) {
        if (reducedNames.size() == 0)
            return new ArrayList<>();

        List<String> unavailableNamesCache = nameCache.lockNameList(reducedNames);
        if (unavailableNamesCache.size() > 0) {
            return unavailableNamesCache;
        }

        List<String> unavailableNamesLedger = ledger.isAllNameRecordsAvailable(reducedNames);
        if (unavailableNamesLedger.size() > 0) {
            nameCache.unlockNameList(reducedNames);
            return unavailableNamesLedger;
        }

        return new ArrayList<>();
    }

    private List<String> isOriginsAvailable(Collection<HashId> origins) {
        if (origins.size() == 0)
            return new ArrayList<>();

        List<String> unavailableOriginsCache = nameCache.lockOriginList(origins);
        if (unavailableOriginsCache.size() > 0) {
            return unavailableOriginsCache;
        }

        List<String> unavailableOriginsLedger = ledger.isAllOriginsAvailable(origins);
        if (unavailableOriginsLedger.size() > 0) {
            nameCache.unlockOriginList(origins);
            return unavailableOriginsLedger;
        }

        return new ArrayList<>();
    }

    private List<String> isAddressesAvailable(Collection<String> addresses) {
        if (addresses.size() == 0)
            return new ArrayList<>();

        List<String> unavailableAddressesCache = nameCache.lockAddressList(addresses);
        if (unavailableAddressesCache.size() > 0) {
            return unavailableAddressesCache;
        }

        List<String> unavailableAddressesLedger = ledger.isAllAddressesAvailable(addresses);
        if (unavailableAddressesLedger.size() > 0) {
            nameCache.unlockAddressList(addresses);
            return unavailableAddressesLedger;
        }

        return new ArrayList<>();
    }

    public NMutableEnvironment getMutable() {
        return new NMutableEnvironment(this);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public FollowerService getFollowerService() { return getFollowerService(false); }

    @Override
    public FollowerService getFollowerService(boolean init) {
        if (init && (followerService == null))
            followerService = new NFollowerService(ledger, id);

        return followerService;
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.set("contract", contract.getPackedTransaction());
        data.set("createdAt", serializer.serialize(createdAt));
        data.set("subscriptions", serializer.serialize(Do.list(subscriptionsSet)));
        data.set("storages", serializer.serialize(Do.list(storagesSet)));
        data.set("nameRecords", serializer.serialize(Do.list(nameRecordsSet)));
        data.set("kvStore", serializer.serialize(kvStore));

        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        createdAt = deserializer.deserialize(data.getZonedDateTimeOrThrow("createdAt"));
        subscriptionsSet.addAll(deserializer.deserialize(data.getListOrThrow("subscriptions")));
        storagesSet.addAll(deserializer.deserialize(data.getListOrThrow("storages")));
        nameRecordsSet.addAll(deserializer.deserialize(data.getListOrThrow("nameRecords")));
        contract = (NSmartContract) Contract.fromPackedTransaction(data.getBinary("contract"));
        kvStore = deserializer.deserialize(data.getBinderOrThrow("kvStore"));
    }

    static {
        Config.forceInit(NImmutableEnvironment.class);
        Config.forceInit(NNameRecord.class);
        Config.forceInit(NNameRecordEntry.class);
        Config.forceInit(NContractSubscription.class);
        Config.forceInit(NContractStorage.class);
        Config.forceInit(NFollowerService.class);
        DefaultBiMapper.registerClass(NImmutableEnvironment.class);
        DefaultBiMapper.registerClass(NNameRecord.class);
        DefaultBiMapper.registerClass(NNameRecordEntry.class);
        DefaultBiMapper.registerClass(NContractSubscription.class);
        DefaultBiMapper.registerClass(NContractStorage.class);
        DefaultBiMapper.registerClass(NFollowerService.class);
    }

    public void setContract(NSmartContract smartContract) {
        contract = smartContract;
    }
}
