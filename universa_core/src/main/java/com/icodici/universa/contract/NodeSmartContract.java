package com.icodici.universa.contract;

import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Node-side smart contract handler.
 * <p>
 * Used to implement node-side smart contract functionality (e.g. slot contract and other incoming types)
 */
public interface NodeSmartContract {

    /**
     * This is a string tag which is used to find the proper {@link com.icodici.universa.contract.NodeSmartContract}
     * implementation.
     *
     * @return string tag, e.g. "SLOT1"
     */
    @NonNull String getExtendedType();

    /**
     * Check the smartcontract could be created
     *
     * @param c
     *         contract in question
     *
     * @return true it if can be created
     */
    boolean beforeCreate(Contract c);

    /**
     * Check the smartcontract could be updated (e.g. new revision could be registered)
     *
     * @param c
     *         contract in question
     *
     * @return true it if can be created
     */
    boolean beforeUpdate(Contract c);

    /**
     * Check the smartcontract could be revoked
     *
     * @param c
     *         contract in question
     *
     * @return true it if can be created
     */

    boolean beforeRevoke(Contract c);


    /**
     * Called after the new contract is approved by the network.
     *
     * @param c
     *         contract just approved
     *
     * @return extra data to pass to the calling client or null
     */
    @Nullable Binder onCreated(Contract c);

    /**
     * Called after the new contract revision is approved by the network.
     *
     * @param c
     *         contract
     *
     * @return extra data to pass to the calling client or null
     */
    @Nullable Binder onUpdate(Contract c);

    /**
     * Called when the contract is just revoked by the network
     *
     * @param c
     */
    void onRevoke(Contract c);

    /**
     * Call the readonly method (query) that does not change the contract inner state (neither the contract nor
     * any associated data) and return the result
     *
     * @param methodName
     * @param params or null
     * @return the results
     */
    @NonNull Binder query(String methodName, Binder params);
}
