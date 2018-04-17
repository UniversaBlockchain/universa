package com.icodici.universa.contract;

/**
 * Node-side smart contract handler.
 *
 * Used to implement node-side smart contract functionality (e.g. slot contract and other incoming
 * types)
 */
public interface NodeSmartContract {

    /**
     * This is a string tag which is used to find the proper {@link com.icodici.universa.contract.NodeSmartContract}
     * implementation.
     *
     * @return string tag, e.g. "SLOT1"
     */
    String getExtendedType();

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
     */
    void onCreated(Contract c);

    /**
     * Called after the new contract revision is approved by the network.
     *
     * @param c
     *         contract
     */
    void onUpdate(Contract c);

    /**
     * Called when the contract is just revoked by the network
     * @param c
     */
    void onRevoke(Contract c);
}
