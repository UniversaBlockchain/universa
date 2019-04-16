package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.ItemState;

import java.time.ZonedDateTime;

/**
 * Service for storing information about subscriptions to callbacks in the follower contract.
 * Allows you to receive and set the time to which it is possible to sending callbacks,
 * the amount of spent U for sending callbacks, the number of running callbacks.
 */
public interface FollowerService {

    /**
     * Get expiration time for callback subscription.
     *
     * @return expiresAt is expiration time
     */
    ZonedDateTime expiresAt();

    /**
     * Getting the time to which it is possible to send callbacks (as long as there is enough money to send at least 1 callback).
     *
     * @return mutedAt is muted time
     */
    ZonedDateTime mutedAt();

    /**
     * Set expiration time for follower service.
     *
     * @param expiresAt is expiration time for follower service
     */
    void setExpiresAt(ZonedDateTime expiresAt);

    /**
     * Set muted time for follower service.
     * Muted follower service is not removed from the ledger, but callbacks are no longer executed (due to lack of funds).
     *
     * @param mutedAt is muted time for follower service
     */
    void setMutedAt(ZonedDateTime mutedAt);

    /**
     * Set expiration and muted time for follower service.
     * Muted follower service is not removed from the ledger, but callbacks are no longer executed (due to lack of funds).
     *
     * @param expiresAt is expiration time for follower service
     * @param mutedAt is muted time for follower service
     */
    void setExpiresAndMutedAt(ZonedDateTime expiresAt, ZonedDateTime mutedAt);

    /**
     * Get origin-days spent for callbacks in follower service
     *
     * @return spent origin-days
     */
    double getCallbacksSpent();

    /**
     * Get number of started callbacks in follower service
     *
     * @return number of started callbacks
     */
    int getStartedCallbacks();

    /**
     * Decrease time to expiration follower service
     *
     * @param decreaseSeconds is interval in seconds for which subscription time is reduced
     */
    void decreaseExpiresAt(int decreaseSeconds);

    /**
     * Change muted time of follower service
     *
     * @param deltaSeconds is interval in seconds for which subscription muted time is changed
     */
    void changeMutedAt(int deltaSeconds);

    /**
     * Increase origin-days spent for callbacks of follower service
     *
     * @param addSpent is spent origin-days
     */
    void increaseCallbacksSpent(double addSpent);

    /**
     * Increment number of started callbacks of follower service
     */
    void increaseStartedCallbacks();

    /**
     * Decrement number of started callbacks of follower service
     */
    void decreaseStartedCallbacks();

    /**
     * Schedule callback processor for one callback.
     *
     * @param updatingItem is new revision of following contract
     * @param state is state of new revision of following contract
     * @param contract is follower contract
     * @param me is environment
     * @param callbackService is node callback service
     */
    void scheduleCallbackProcessor(Contract updatingItem, ItemState state, NSmartContract contract,
                                   MutableEnvironment me, CallbackService callbackService);

    /**
     * Save changes in follower service to ledger
     */
    void save();
}
