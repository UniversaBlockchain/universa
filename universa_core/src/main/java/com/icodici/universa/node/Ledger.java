/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.crypto.PrivateKey;
import com.icodici.db.Db;
import com.icodici.universa.HashId;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;

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
     * @return
     */
    StateRecord findOrCreate(HashId itemdId);

    /**
     * Shortcut method: check that record exists and its state returns {@link ItemState#isApproved()}}. Check it to
     * ensure its meaning.
     *
     * @param id
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
     * @param id
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
     * @return null if transaction is rolled back throwing a {@link Rollback} exception, otherwise what callable
     * returns.
     */
    <T> T transaction(Callable<T> callable);

    /**
     * Destroy the record and free space in the ledger.
     *
     * @param record
     */
    void destroy(StateRecord record);

    /**
     * save a record into the ledger
     *
     * @param stateRecord
     */
    void save(StateRecord stateRecord);

    /**
     * Refresh record.
     *
     * @param stateRecord
     */
    void reload(StateRecord stateRecord) throws StateRecord.NotFoundException;

    default void close() {}

    default long countRecords() {
        return -1;
    }

    default StateRecord getLockOwnerOf(HashId itemId) {
        return getLockOwnerOf(getRecord(itemId));
    }

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

}
