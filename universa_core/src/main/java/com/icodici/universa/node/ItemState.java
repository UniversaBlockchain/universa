/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;

/**
 * States of the {@link Approvable} item.
 * <p>
 * Allowed states:
 * <pre>
 * (UNDEFINED)
 *      --> PENDING
 *              --> (destroyed by expiration)
 *              --> PENDING_NEGATIVE | PENDING_POSITIVE
 *                      --> APPROVED
 *                      --> DECLINED
 *                              --> (destroyed by expiration)
 *              --> APPROVED
 *              --> DECLINED
 *                      --> (destroyed by expiration)
 *
 *       --> LOCKED_FOR_CREATION
 *              --> APPOVED
 *              --> (destroyed)
 * ------------------------------------------------------------
 * (APPROVED)
 *      --> LOCKED
 *              --> APPROVED
 *      --> REVOKED
 *              --> (destroyed by expiration)
 * </pre>
 */
public enum ItemState {
    /**
     * Bad state, can't process. For example, structure that is not yet initialized. Otherwise, the contract us unknown
     * to the system, which could be used in the client API calls, to check the state of the exisitng contract.
     */
    UNDEFINED,
    /**
     * The contract is being processed. No positive or negative local solution is yet found. For example, the contract
     * is being downloaded or being checked locally. This state requires calling party to repeat the inquiry later.
     */
    PENDING,
    /**
     * The contract is locally checked and found OK against local ledger, voting is in progress and no consensus is
     * found.
     */
    PENDING_POSITIVE,
    /**
     * The contract is locally checked and found bad, but voting is yet in progress and yet no consensus is found.
     */
    PENDING_NEGATIVE,
    /**
     * The positive consensus is found for the contract, it was approved by the network and is not yet revoked.
     */
    APPROVED,
    /**
     * The item is locked for revokation by some transactoin
     */
    LOCKED,
    /**
     * The contract once approved by the netowork is now revoked and is neing kept in archive for approproate time.
     * Archived signatures are kept only the time needed to prevent some sort of attacks and procees with any support
     * requests. It could be, for example, 90 days.
     */
    REVOKED,
    /**
     * The contract was checked by the netwokr and negative consensus was found. Declined signatures are kept, like
     * REVOKED contratcs, a limited time and with for the same reasons.
     */
    DECLINED,
    /**
     * the item must be discarded without further processing
     */
    DISCARDED,
    /**
     * Special state: locked by another mending item that will create and approce this item if approved by the
     * consensus. This state is separated from others to detect attempt to create same item by different racing items
     * being voted, so only one os them will succeed, as only one of them will succeed to lock for creation its output
     * documents.
     */
    LOCKED_FOR_CREATION;

    /**
     * Check that either positive or negative consensus was found
     *
     * @return
     */
    public boolean consensusFound() {
        switch (this) {
            case LOCKED:
            case APPROVED:
            case REVOKED:
            case DECLINED:
                return true;
        }
        return false;
    }

    /**
     * Check that state means the item is approved and not archived, e.g. active and eligible for referencing, etc. It
     * includes the case when the item is already locked for revocation but is not yet archived, at this time the item
     * is still considered as approved by the consensus.
     *
     * @return true if it is
     */
    public boolean isApproved() {
        return this == APPROVED || this == LOCKED;
    }

    /**
     * Check that state means the item is being processed by the network and no consensus yet is found.
     *
     * @return true if it is being processed
     */
    public boolean isPending() {
        return this == PENDING || this == PENDING_NEGATIVE || this == PENDING_POSITIVE;
    }

    public boolean isPositive() {
        return isApproved() || this == PENDING_POSITIVE;
    }
}
