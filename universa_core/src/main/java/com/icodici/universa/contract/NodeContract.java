package com.icodici.universa.contract;

import com.icodici.universa.contract.services.ImmutableEnvironment;
import com.icodici.universa.contract.services.MutableEnvironment;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Node-side smart contract handler.
 * <p>
 * Used to implement node-side smart contract functionality (e.g. slot contract and other incoming types)
 */
public interface NodeContract {

    /**
     * This is a string tag which is used to find the proper {@link NodeContract}
     * implementation.
     *
     * @return string tag, e.g. "SLOT1"
     */
    @NonNull String getExtendedType();

    /**
     * Check the smart contract could be created
     *
     * @return true it if can be created
     */
    boolean beforeCreate(ImmutableEnvironment e);

    /**
     * Check the smart contract could be updated (e.g. new revision could be registered)
     *
     * @return true it if can be created
     */
    boolean beforeUpdate(ImmutableEnvironment e);

    /**
     * Check the smart contract could be revoked
     *
     * @return true it if can be created
     */

    boolean beforeRevoke(ImmutableEnvironment e);


    /**
     * Called after the new contract is approved by the network.
     *
     * @return extra data to pass to the calling client or null
     */
    @Nullable Binder onCreated(MutableEnvironment me);

    /**
     * Called after the new contract revision is approved by the network.
     *
     * @return extra data to pass to the calling client or null
     */
    @Nullable Binder onUpdated(MutableEnvironment me);

    /**
     * Called when the contract is just revoked by the network
     */
    void onRevoked(ImmutableEnvironment me);

    /**
     * Call the readonly method (query) that does not change the contract inner state (neither the contract nor
     * any associated data) and return the result
     *
     * @param methodName
     * @param params or null
     * @return the results
     */
    @NonNull Binder query(ImmutableEnvironment e,String methodName, Binder params);
}
