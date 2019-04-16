package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
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

public interface ContractStorage {

    /**
     * Get expiration time for contract storage.
     *
     * @return expiration time for contract storage
     */
    ZonedDateTime expiresAt();

    /**
     * @return the unpacked stored contract. Note that this instance could be cached/shared among subscribers.
     */
    Contract getContract();

    /**
     * @return stored packed representation (transaction pack)
     */
    byte[] getPackedContract();

    /**
     * Set expiration time for contract storage.
     *
     * @param expiredAt is expiration time for contract storage
     */
    void setExpiresAt(ZonedDateTime expiredAt);
}
