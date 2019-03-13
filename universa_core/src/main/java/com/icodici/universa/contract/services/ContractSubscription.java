package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node2.CallbackService;

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

public interface ContractSubscription {

    /**
     * @return the expiration time of subscription.
     */
    ZonedDateTime expiresAt();

    /**
     * @return the {@link HashId} of subscribed contract or contracts chain.
     */
    HashId getHashId();

    /**
     * @return the id of subscribed contract.
     */
    HashId getContractId();

    /**
     * @return the origin of contracts chain of subscription.
     */
    HashId getOrigin();

    /**
     * @return true if subscription for contracts chain.
     */
    boolean isChainSubscription();

    /**
     * Set expiration time of subscription.
     *
     * @param expiredAt is expiration time of subscription
     */
    void setExpiresAt(ZonedDateTime expiredAt);

    /**
     * The subscription event base interface.
     */
    interface Event {
        MutableEnvironment getEnvironment();
    }

    /**
     * The subscription event base interface for storage subscription.
     * Real events are either {@link ApprovedEvent} or {@link RevokedEvent} implementations.
     */
    interface SubscriptionEvent extends Event {
        ContractSubscription getSubscription();
    }

    interface ApprovedEvent extends SubscriptionEvent {
        /**
         * @return new revision just approved as the Contract
         */
        Contract getNewRevision();

        /**
         * @return Packed transaction of the new revision just approved
         */
        byte[] getPackedTransaction();
    }

    interface RevokedEvent extends SubscriptionEvent {}

    /**
     * The subscription event base interface for starting follower callback.
     * Real events are either {@link ApprovedWithCallbackEvent} or {@link RevokedWithCallbackEvent} implementations.
     */
    interface CallbackEvent extends Event {
        /**
         * @return service for callback sending
         */
        //TODO: callback service should be an interface inside  com.icodici.universa.contract.services. Implementation of this interface in module universa_node should have access to the actuall callback service
        CallbackService getCallbackService();
    }

    interface ApprovedWithCallbackEvent extends CallbackEvent {
        /**
         * @return new revision just approved as the Contract
         */
        Contract getNewRevision();
    }

    interface RevokedWithCallbackEvent extends CallbackEvent {
        /**
         * @return revoking item as the Contract
         */
        Contract getRevokingItem();
    }

    interface CompletedEvent extends Event {}

    interface FailedEvent extends Event {}

    interface SpentEvent extends Event {}
}
