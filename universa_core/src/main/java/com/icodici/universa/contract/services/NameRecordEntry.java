package com.icodici.universa.contract.services;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;

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

public interface NameRecordEntry {

    String getLongAddress();
    String getShortAddress();
    HashId getOrigin();

}
