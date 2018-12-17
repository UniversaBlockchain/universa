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
     *
     * @return follower subscription
     */
    @NonNull ContractSubscription createChainSubscription(@NonNull HashId origin, @NonNull ZonedDateTime expiresAt);

    /**
     * Create subscription to a packed contract. It always creates the subscription to new or existing contract.
     *
     * @param id is contract identifier
     * @param expiresAt is time to expiration subscription
     *
     * @return storage subscription
     */
    @NonNull ContractSubscription createContractSubscription(@NonNull HashId id, @NonNull ZonedDateTime expiresAt);

    /**
     * Create storage for a packed contract.
     * The storage must not create copies of the same contracts or update its stored binary representations. There
     * should be always no one copy in the storage.
     *
     * @param packedTransaction is a packed {@link com.icodici.universa.contract.TransactionPack} with contract
     * @param expiresAt is time to expiration subscription
     *
     * @return storage subscription
     */
    @NonNull NContractStorage createContractStorage(byte[] packedTransaction, @NonNull ZonedDateTime expiresAt);


    @NonNull NameRecord createNameRecord(@NonNull UnsName unsName, @NonNull ZonedDateTime expiresAt);

    /**
     * Set expiration time for subscription
     *
     * @param subscription
     * @param expiresAt is time to expiration subscription
     */
    void setSubscriptionExpiresAt(ContractSubscription subscription, ZonedDateTime expiresAt);

    /**
     * Set expiration time for contract storage
     *
     * @param storage is contract storage
     * @param expiresAt is time to expiration contract storage
     */
    void setStorageExpiresAt(ContractStorage storage, ZonedDateTime expiresAt);

    /**
     * Remove subscription from the ledger
     *
     * @param subscription
     */
    void destroySubscription(ContractSubscription subscription);

    /**
     * Remove stored contract from the ledger
     *
     * @param contractStorage is contract storage
     */
    void destroyStorage(ContractStorage contractStorage);

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
}
