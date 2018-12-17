package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node2.CallbackService;

import java.time.ZonedDateTime;

//*

public interface FollowerService {

    ZonedDateTime expiresAt();

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
     * Save changes in follower service to ledger
     */
    void save();
}
