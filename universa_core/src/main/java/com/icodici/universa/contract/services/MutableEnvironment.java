package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.ZonedDateTime;

/**
 * The RW envitonment for {@link NContract} instance, where it can change its server state.
 * <p>
 * It implemets KV store for the server-state. It is created automatically first time {@link #set(String, Object)} is
 * called and must commit any changes to the ledger when the new contract state is being approved. Before this the
 * ledger state must not be altered.
 * <p>
 * The RC problem should be repelled by saving the state of the "approved" contract only. To do this. the nodes must
 * extend voting by adding state's CRC2-384 hash to the voting ID and copy the state of the approved contract as need
 */
public interface MutableEnvironment extends ImmutableEnvironment {

    /**
     * Writes a key to the KV store. See the logic description above.
     *
     * @param key
     * @param value
     * @param <T>
     *
     * @return
     */
    <T extends Object> T set(String key, T value);

    /**
     * Reset the KV store to the initial state
     */
    void rollback();

    /**
     * Create follower subscription to the chain of contracts
     *
     * @param origin of contracts chain
     * @param expiresAt is time to expiration subscription
     * @param mutedAt is time to muted (not send callbacks due to insufficient payments)
     *
     * @return follower subscription
     */
    @Nullable ContractSubscription createFollowerSubscription(@NonNull HashId origin, @NonNull ZonedDateTime expiresAt,
                                                              @NonNull ZonedDateTime mutedAt);

    /**
     * Create storage subscription to a packed contract. It always creates the subscription to new or existing contract.
     * The storage must not create copies of the same contracts or update its stored binary representations. There
     * should be always no one copy in the storage.
     *
     * @param packedTransaction is a packed {@link com.icodici.universa.contract.TransactionPack} with contract
     * @param expiresAt is time to expiration subscription
     *
     * @return storage subscription
     */
    @NonNull ContractSubscription createStorageSubscription(byte[] packedTransaction,
                                                                   @NonNull ZonedDateTime expiresAt);


    @NonNull NameRecord createNameRecord(@NonNull UnsName unsName,
                                              @NonNull ZonedDateTime expiresAt);

    /**
     * Set expiration time for subscription
     *
     * @param subscription
     * @param expiresAt is time to expiration subscription
     */
    void setSubscriptionExpiresAt(ContractSubscription subscription, ZonedDateTime expiresAt);

    /**
     * Set expiration and muted time for follower subscription.
     * Muted follower subscription is not removed from the ledger, but callbacks are no longer executed (due to lack of funds).
     *
     * @param subscription is follower subscription
     * @param expiresAt is time to expiration subscription
     * @param mutedAt is muted time of subscription
     */
    void setSubscriptionExpiresAtAndMutedAt(ContractSubscription subscription, ZonedDateTime expiresAt, ZonedDateTime mutedAt);

    /**
     * Remove subscription from the ledger
     *
     * @param subscription
     */
    void destroySubscription(ContractSubscription subscription);

    /**
     * Set expiration time for storing UNS name
     *
     * @param nameRecord is UNS name record
     * @param expiresAt is time to expiration UNS name
     */
    void setNameRecordExpiresAt(NameRecord nameRecord, ZonedDateTime expiresAt);

    /**
     * Remove UNS name from the ledger
     *
     * @param nameRecord is UNS name record
     */
    void destroyNameRecord(NameRecord nameRecord);

    /**
     * Get origin-days spent for callbacks by all subscriptions in this environment
     *
     * @return spent origin-days
     */
    double getSubscriptionsCallbacksSpentODs();

    /**
     * Get number of started callbacks by all subscriptions in this environment
     *
     * @return number of started callbacks
     */
    int getSubscriptionsStartedCallbacks();

    /**
     * Decrease time to expiration follower subscription
     *
     * @param subscription is follower subscription
     * @param decreaseSeconds is interval in seconds for which subscription time is reduced
     */
    void decreaseSubscriptionExpiresAt(ContractSubscription subscription, int decreaseSeconds);

    /**
     * Change muted time of follower subscription
     *
     * @param subscription is follower subscription
     * @param deltaSeconds is interval in seconds for which subscription muted time is changed
     */
    void changeSubscriptionMutedAt(ContractSubscription subscription, int deltaSeconds);

    /**
     * Increase origin-days spent for callbacks of follower subscription
     *
     * @param subscription is follower subscription
     * @param addSpent is spent origin-days
     */
    void increaseCallbacksSpent(ContractSubscription subscription, double addSpent);

    /**
     * Increment number of started callbacks of follower subscription
     *
     * @param subscription is follower subscription
     */
    void increaseStartedCallbacks(ContractSubscription subscription);

    /**
     * Decrement number of started callbacks of follower subscription
     *
     * @param subscription is follower subscription
     */
    void decreaseStartedCallbacks(ContractSubscription subscription);
}
