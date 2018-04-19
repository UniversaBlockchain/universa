package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;

import java.time.ZonedDateTime;

/**
 * Subscription to store one revision of the packed contract (transaction pack)
 * <p>
 * The subscribers ({@link NContract} instances) subscribe to contracts to store them for some amount of time. All
 * subscriptions share same copy of the stored contract. When the last susbscription to this revision is destroyed or
 * expired, the copy is dropped.
 * <p>
 * Note that subscriptions are private to {@link NContract} instances and visible only to it. When the NContract is
 * revoked, all its subscriptions must be destroyed.
 */

public interface ContractStorageSubscription {

    ZonedDateTime expiresAt();

    void setExpiresAt(ZonedDateTime expiresAt);

    void destroy();

    /**
     * @return the unpacked stored contract. Note that this instance could be cached/shared among subscribers.
     */
    Contract getContract();

    /**
     * @return stored packed representation (transaction pack)
     */
    byte[] getPackedContract();

    /**
     * The subscription event
     */
    interface Event {
        /**
         * New revision of the stored contract is just approved. Subscriber must decide to drop subscription and
         * subscribe to the new revision
         * @param subscription
         * @param newRevision
         * @param newPackedRevision
         */
        void onApproved(ContractStorageSubscription subscription,Contract newRevision,byte[] newPackedRevision);

        /**
         * The subscribed contract is revoked. It is possible to keep stored also revoked revisions, though
         * subscriber might often find dropping revoked items more appropriate.
         * @param subscription
         */
        void onRevoked(ContractStorageSubscription subscription);
    }

    /**
     * Allow {@link NContract} to receive (or not) events with {@link Event}, with {@link
     * NContract#onContractStorageSubscriptionEvent(Event)}
     *
     * @param doRecevie true to receive events, false to stop
     */
    void receiveEvents(boolean doRecevie);
}
