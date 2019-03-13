package com.icodici.universa.contract.services;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.ItemState;

/**
 * Universa network callback service interface. The service creates a callback processor, sends a callback to distant callback URL,
 * notifies the follower contract of a state change, notifies the other network nodes, and synchronizes the callback states.
 */
public interface CallbackService {
    /**
     * Runs callback processor for one callback. Adds callback record to ledger, runs callback processing thread and
     * checks and obtains deferred callback notifications.
     *
     * @param updatingItem is new revision of following contract
     * @param state is state of new revision of following contract
     * @param contract is follower contract
     * @param me is environment
     */
    public void startCallbackProcessor(Contract updatingItem, ItemState state, NSmartContract contract, MutableEnvironment me);
}
