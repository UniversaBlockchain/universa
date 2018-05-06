/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.node2.Quantiser;

import java.time.ZonedDateTime;
import java.util.*;

/**
 * Interface to anything that could be approved by the Universa network. Any entity, not limiting to the SmartContract,
 * could be used.
 * <p>
 * Approvable logic:
 * <p>
 * Approvable instance provides self {@link #check()}, and list of other items. Note that transaction that have no
 * output, e.g. empty {@link #getRevokingItems()} and {@link #getNewItems()} will not change the ledger and therefore
 * will not be processed by the network.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public interface Approvable extends HashIdentifiable {

    /**
     * Map of references that item have.
     *
     * @return referenced items map
     */
    default HashMap<String, Reference> getReferences() {
        return new HashMap<>();
    }

    /**
     * List of items (e.g. smartcontracts) that should be valid until the end of the operation.
     * These items will be
     * write-locked until the consensus is found.
     *
     * @return referenced items list
     */
    default Set<Approvable> getReferencedItems() {
        return new HashSet<>();
    }

    /**
     * List of items that will be revoked on positive consensus. These items will be exclusively locked until the
     * transaction is done. On success (positive consensus) these items will be revoked!
     *
     * @return list of items revoked by this transaction.
     */
    default Set<Approvable> getRevokingItems() {
        return new HashSet<>();
    }

    /**
     * get items created by this transaction. Note, that if the Approvable items does not add self to this list, it will
     * not receive approved state on the positive consensus. Thus, the transactional Apptovable instance could create
     * self, or create other instances on success.
     *
     * @return list of items to approve.
     */
    default Set<Approvable> getNewItems() {
        return new HashSet<Approvable>();
    }

    /**
     * Check the the document is valid assuming all mentioned items are OK, e.g. items, approved by it (if any),
     * items revoking by it and referenced  by it.
     *
     * @param prefix is for marking checking item
     * @return true if this instance is completely checked with positive result.
     * @throws Quantiser.QuantiserException if processing cost limit is got
     */
    boolean check(String prefix) throws Quantiser.QuantiserException;

    default boolean check() throws Quantiser.QuantiserException {
        return check("");
    }

    /**
     * Special check for U contracts (used as payment for {@link com.icodici.universa.contract.Parcel}).
     * Use {@link Approvable#shouldBeU()} to know if item should checked using this method.
     * @param issuerKeys is set of keys allowed for U issuing
     * @return true if checking passed successfully otherwise false.
     * @throws Quantiser.QuantiserException
     */
    default boolean paymentCheck(Set<KeyAddress> issuerKeys) throws Quantiser.QuantiserException {
        return false;
    }

    default void addError(ErrorRecord r) {
    }

    default ZonedDateTime getCreatedAt() {
        return ZonedDateTime.now();
    }

    default void addError(Errors code,String object,String message) {
        addError(new ErrorRecord(code, object, message));
    }

    default Collection<ErrorRecord> getErrors() {
        return Collections.emptyList();
    }

    default ZonedDateTime getExpiresAt() { return ZonedDateTime.now().plusHours(5);}

    /**
     * Special check for item to pass or not U contract criteria: allowed issuer keys and issuer name
     * @param issuerKeys is set of allowed by node keys for U issuing
     * @param issuerName is allowed issuer name
     * @return true id item match with given criteria, otherwise false.
     */
    default boolean isU(Set<KeyAddress> issuerKeys, String issuerName) {
        return false;
    }

    default boolean isInWhiteList(List<PublicKey> whiteList) {
        return false;
    }

    /**
     * Getter for the node that shows for the node if item should be U contract and should check with special payment check.
     * Set value for this getter should be safe, i.e. on the node while unpacking or later.
     *
     * @return true if item is U contract.
     */
    default boolean shouldBeU() {
        return false;
    }
}
