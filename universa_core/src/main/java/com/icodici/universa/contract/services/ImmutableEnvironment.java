package com.icodici.universa.contract.services;

import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

/**
 * The environment accessible to readonly {@link NContract} methods, e.g. {@link
 * NContract#query(ImmutableEnvironment, String, Binder)} and {@link NContract#onRevoked(ImmutableEnvironment)} and like.
 *
 * Note tha the envidonment associated with {@link NContract} must be destroyed when the NContract is revoked.
 */
public interface ImmutableEnvironment {

    /**
     * There is always and instance of the contract available.
     *
     * @param <T>
     *         contract type
     *
     * @return the contract that this environment is created for.
     */
    @NonNull <T extends Contract> T getContract();

    /**
     * Read access to the instance server-size key-value store. Note that if the store is not created, it always return
     * default value, this is not an error.
     *
     * @param keyName
     *         key name
     * @param defaultValue
     *         value to return if the KV store is empty
     * @param <T>
     * @param <U>
     *
     * @return the stored value or the default value
     */
    <T, U extends T> T get(String keyName, U defaultValue);

    /**
     * The instance when this contract was created at THIS NODE (calling Node). This is effectively the time when the
     * node has started processing the contract, the {@link com.icodici.universa.node.Ledger} stored, e.g. {@link
     * com.icodici.universa.node.StateRecord#createdAt} will be the right value.
     *
     * @return
     */
    @NonNull ZonedDateTime instanceCreatedAt();

    Iterable<ContractStorageSubscription> storageSubscriptions();
    Iterable<NameRecord> nameRecords();

    List<ErrorRecord> tryAllocate(Collection<String> reducedNamesToAllocate, Collection<HashId> originsToAllocate, Collection<String> addressesToAllocate);
}
