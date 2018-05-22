package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.node.models.NameRecordModel;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.ZonedDateTime;

/**
 * The RW envitonment for {@link NodeContract} instance, where it can change its server state.
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
     * Create subscription to the existing (stored) contract
     *
     * @param contractId
     *         stored revision
     * @param expiresAt
     *         time to expiration
     *
     * @return subscription or null if this contract is not known to the storage service
     */
    @Nullable ContractStorageSubscription createStorageSubscription(@NonNull HashId contractId,
                                                                    @NonNull ZonedDateTime expiresAt);

    /**
     * Create storage subscription to a packed contract. It always creates the subscription to new or existing contract.
     * The storage must not create copies of the same contracts or update its storded binary representations. There
     * should be always no one copy in the storage
     *
     * @param packedTransaction
     * @param expiresAt
     *
     * @return susbscription.
     */
    @NonNull ContractStorageSubscription createStorageSubscription(byte[] packedTransaction,
                                                                   @NonNull ZonedDateTime expiresAt);


    @NonNull NameRecord createNameRecord(@NonNull UnsName unsName,
                                              @NonNull ZonedDateTime expiresAt);

    void setSubscriptionExpiresAt(ContractStorageSubscription subscription, ZonedDateTime expiresAt);

    void destroySubscription(ContractStorageSubscription subscription);


    void setNameRecordExpiresAt(NameRecord nameRecord, ZonedDateTime expiresAt);

    void destroyNameRecord(NameRecord nameRecord);


}
