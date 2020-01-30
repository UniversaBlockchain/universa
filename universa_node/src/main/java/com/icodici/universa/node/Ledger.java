/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.db.Db;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node2.*;
import net.sergeych.tools.Binder;

import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Local storage for {@link StateRecord} objects, sort of a database. The implementations should not, except where
 * noted, implement any business logic, which is incapsulated in {@link StateRecord} and
 * {@link com.icodici.universa.node2.Node} classes. This is only some type of a storage.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public interface Ledger {

    /**
     * Get the record by its id
     *
     * @param id to retreive
     * @return instance or null if not found
     */
    StateRecord getRecord(HashId id);

    /**
     * Create a record in {@link ItemState#LOCKED_FOR_CREATION} state locked by creatorRecordId. Does not check
     * anything, the business logic of it is in the {@link StateRecord}. Still, if a database logic prevents creation of
     * a lock record (e.g. hash is already in use), it must return null.
     *
     * @param creatorRecordId record that want to create new item
     * @param newItemHashId   new item hash
     * @return ready saved instance or null if it can not be created (e.g. already exists)
     */
    StateRecord createOutputLockRecord(long creatorRecordId, HashId newItemHashId);

    /**
     * Get the record that owns the lock. This method should only return the record, not analyze it or somehow process. Still
     * it never returns expired records. Note that <b>caller must clear the lock</b> if this method returns null.
     *
     * @param rc locked record.
     * @return the record or null if none found
     */
    StateRecord getLockOwnerOf(StateRecord rc);

    /**
     * Create new record for a given id and set it to the PENDING state. Normally, it is used to create new root
     * documents. If the record exists, it returns it. If the record does not exists, it creates new one with {@link
     * ItemState#PENDING} state. The operation must be implemented as atomic.
     *
     * @param itemdId hashId to register, or null if it is already in use
     * @return found or created {@link StateRecord}
     */
    StateRecord findOrCreate(HashId itemdId);

    /**
     * Shortcut method: check that record exists and its state returns {@link ItemState#isApproved()}}. Check it to
     * ensure its meaning.
     *
     * @param id is {@link HashId} for checking item
     * @return true if it is.
     */
    default boolean isApproved(HashId id) {
        StateRecord r = getRecord(id);
        return r != null && r.getState().isApproved();
    }

    /**
     * Shortcut method: check that record exists and its state returns {@link ItemState#isConsensusFound()}}. Check it to
     * ensure its meaning.
     *
     * @param id is {@link HashId} for checking item
     * @return true if it is.
     */
    default boolean isConsensusFound(HashId id) {
        StateRecord r = getRecord(id);
        return r != null && r.getState().isConsensusFound();
    }

    /**
     * Perform a callable in a transaction. If the callable throws any exception, the transaction should be rolled back
     * to its initial state. Blocks until the callable returns, and returns what the callable returns. If an exception
     * is thrown by the callable, the transaction is rolled back and the exception will be rethrown unless it was a
     * {@link Rollback} instance, which just rollbacks the transaction, in which case it always return null.
     *
     * @param callable to execute
     * @param <T> is type
     * @return null if transaction is rolled back throwing a {@link Rollback} exception, otherwise what callable
     * returns.
     */
    <T> T transaction(Callable<T> callable);

    /**
     * Destroy the record and free space in the ledger.
     *
     * @param record is {@link StateRecord} to destroy
     */
    void destroy(StateRecord record);

    /**
     * save a record into the ledger
     *
     * @param stateRecord is {@link StateRecord} to save
     */
    void save(StateRecord stateRecord);

    /**
     * Refresh record.
     *
     * @param stateRecord is {@link StateRecord} to reload
     * @throws StateRecord.NotFoundException as itself
     */
    void reload(StateRecord stateRecord) throws StateRecord.NotFoundException;

    default void close() {}

    default long countRecords() {
        return -1;
    }

//    /**
//     * Return all records with given {@link ItemState}.
//     *
//     * @param is is {@link ItemState} to looking for.
//     * @return all found records
//     */
//    List<StateRecord> getAllByState(ItemState is);

    default StateRecord getLockOwnerOf(HashId itemId) {
        return getLockOwnerOf(getRecord(itemId));
    }

    Map<ItemState,Integer> getLedgerSize(ZonedDateTime createdAfter);

    public void savePayment(int amount, ZonedDateTime date);
    public Map<Integer,Integer> getPayments( ZonedDateTime fromDate);

    void markTestRecord(HashId hash);

    boolean isTestnet(HashId itemId);

    void updateSubscriptionInStorage(long id, ZonedDateTime expiresAt);
    void updateStorageExpiresAt(long storageId, ZonedDateTime expiresAt);
    void saveFollowerEnvironment(long environmentId, ZonedDateTime expiresAt, ZonedDateTime mutedAt, double spent, int startedCallbacks);

    void updateNameRecord(long id, ZonedDateTime expiresAt);

    Set<HashId> saveEnvironment(NImmutableEnvironment environment);

    Set<HashId> findBadReferencesOf(Set<HashId> ids);


    VoteInfo initiateVoting(Contract contract, ZonedDateTime expiresAt, String roleName, Set<HashId> candidates);
    VoteInfo getVotingInfo(HashId votingId);

    void addVotes(long votingId, long candidateId, List<KeyAddress> votes);

    default List<VoteResult> getVotes(HashId candidateId) {return getVotes(candidateId,false);}
    public List<VoteResult> getVotes(HashId candidateId, boolean queryAddresses);

    void closeVote(HashId itemId);

    class UbotSessionCompact {
        public long id; // record id in database
        public HashId executableContractId;
        public HashId requestId;
        public byte[] requestContract;
        public int state;
        public HashId sessionId;
        public Map<String, Map<Integer,HashId>> storageUpdates;
        public Set<Integer> closeVotes;
        public Set<Integer> closeVotesFinished;
        public int quantaLimit;
        public ZonedDateTime expiresAt;
    };
    void saveUbotSession(UbotSessionCompact sessionCompact);
    UbotSessionCompact loadUbotSession(HashId executableContractId);
    UbotSessionCompact loadUbotSessionById(HashId sessionId);
    UbotSessionCompact loadUbotSessionByRequestId(HashId sessionId);
    boolean hasUbotSession(HashId executableContractId);
    void deleteUbotSession(HashId executableContractId);
    void deleteExpiredUbotSessions();
    void saveUbotStorageValue(HashId executableContractId, ZonedDateTime expiresAt, String storageName, HashId value);
    HashId getUbotStorageValue(HashId executableContractId, String storageName);
    void getUbotStorages(HashId executableContractId, Map<String, HashId> dest);
    void deleteExpiredUbotStorages();


    public static class Rollback extends Db.RollbackException {
    }

    /**
     * Exception (non-checked) class that should be thrown by implementation when inrecoverable errors happen like
     * failre to save value.
     */
    public static class Failure extends RuntimeException {
        public Failure() {
        }

        public Failure(String message) {
            super(message);
        }

        public Failure(String message, Throwable cause) {
            super(message, cause);
        }
    }


    void saveConfig(NodeInfo myInfo, NetConfig netConfig, PrivateKey nodeKey);
    Object[] loadConfig();
    void addNode(NodeInfo nodeInfo);
    void removeNode(NodeInfo nodeInfo);
    Map<HashId,StateRecord> findUnfinished();

    Approvable getItem(StateRecord record);
    void putItem(StateRecord record, Approvable item, Instant keepTill);

    byte[] getKeepingItem(HashId itemId);


    void putKeepingItem(StateRecord record, Approvable item);
    @Deprecated
    Object getKeepingByOrigin(HashId origin, int limit);


    Binder getKeepingBy(String field, HashId id, Binder tags, int limit, int offset, String sortBy, String sortOrder);

    NImmutableEnvironment getEnvironment(long environmentId);
    NImmutableEnvironment getEnvironment(HashId contractId);
    NImmutableEnvironment getEnvironment(NSmartContract smartContract);

    void updateEnvironment(long id, String ncontractType, HashId ncontractHashId, byte[] kvStorage, byte[] transactionPack);

    long saveContractInStorage(HashId contractId, byte[] binData, ZonedDateTime expiresAt, HashId origin, long environmentId);

    long saveSubscriptionInStorage(HashId hashId, boolean subscriptionOnChain, ZonedDateTime expiresAt, long environmentId);

    Set<Long> getSubscriptionEnviromentIds(HashId id);

    NCallbackService.FollowerCallbackState getFollowerCallbackStateById(HashId id);
    Collection<CallbackRecord> getFollowerCallbacksToResyncByEnvId(long environmentId);
    Collection<CallbackRecord> getFollowerCallbacksToResync();
    void addFollowerCallback(HashId id, long environmentId, ZonedDateTime expiresAt, ZonedDateTime storedUntil);
    void updateFollowerCallbackState(HashId id, NCallbackService.FollowerCallbackState state);
    void removeFollowerCallback(HashId id);

    void clearExpiredStorages();
    void clearExpiredSubscriptions();
    void clearExpiredStorageContractBinaries();

    byte[] getSmartContractById(HashId smartContractId);
    byte[] getContractInStorage(HashId contractId);
    byte[] getContractInStorage(HashId slotId, HashId contractId);
    List<byte[]> getContractsInStorageByOrigin(HashId slotId, HashId originId);

    void removeEnvironmentSubscription(long subscriptionId);
    void removeEnvironmentStorage(long storageId);
    long removeEnvironment(HashId ncontractHashId);
    void removeExpiredStoragesAndSubscriptionsCascade();

    void addNameRecord(final NNameRecord nameRecordModel);
    void removeNameRecord(final NNameRecord nameRecordModel);

    void addNameRecordEntry(final NNameRecordEntry nameRecordEntryModel);
    void removeNameRecordEntry(NNameRecordEntry nameRecordEntry);

    NNameRecord getNameRecord(final String nameReduced);
    List<NNameRecordEntry> getNameEntries(final long environmentId);
    List<NNameRecordEntry> getNameEntries(final String nameReduced);
    List<NNameRecord> getNamesByAddress (String address);
    List<NNameRecord> getNamesByOrigin (byte[] origin);
    List<String> isAllNameRecordsAvailable(final Collection<String> reducedNames);
    List<String> isAllOriginsAvailable(final Collection<HashId> origins);
    List<String> isAllAddressesAvailable(final Collection<String> addresses);
    void clearExpiredNameRecords(Duration holdDuration);

    void cleanup(boolean isPermanetMode);

    void saveUbotTransaction(HashId executableContractId, String transactionName, Binder state);
    Binder loadUbotTransaction(HashId executableContractId, String transactionName);
}
