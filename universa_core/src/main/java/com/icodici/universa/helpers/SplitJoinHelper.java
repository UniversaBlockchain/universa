package com.icodici.universa.helpers;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.helpers.Compound;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Prototype to discuss.
 * <p>
 * Note about tags. there is reserved tag, "rest", that could not be used/
 */
public class SplitJoinHelper {

    /**
     * Prevents modifying helper after the compound us built.
     */
    static class CompoundAlreadyBuiltError extends RuntimeException {
    }

    /**
     * Add one more input contract.
     *
     * @param tag       to get it from the compound
     * @param ownerKeys to sign compound with
     * @param source    source contract to be used in this splitjoin
     * @throws CompoundAlreadyBuiltError
     */
    void addInput(String tag, Set<PrivateKey> ownerKeys, Contract source) {
    }

    /**
     * Check that one more input can be added without increasing processing cost (in U)
     *
     * @return true if is it possible
     * @throws CompoundAlreadyBuiltError
     */
    boolean costAllowsAddingInput() {
        return false;
    }

    /**
     * Add output contract. "Rest" contract must not be added specially, helperwill create it automatically.
     * Note that the actual output contract will only be built when build() is called.
     *
     * @param tag      to get the contract from the compound. It is not possible to get the contract before compound is built
     * @param newOwner new owner key address
     * @param amount   to transfer
     * @throws CompoundAlreadyBuiltError
     */
    void addOutput(String tag, KeyAddress newOwner, BigDecimal amount) {
    }

    /**
     * Checks that inputs >= output.
     * <p>
     * Creates optimized compound with properly tagged input and output contracts, and the rest contract tagged "rest".
     * If the "rest" tag does not exist in the compound, it means it is empty (smm(inputs) == sum(outputs). Generated
     * compound must solve A + B -> C + D + A problem as needed.
     * <p>
     * The parts should be available with {@link Compound#getContract(String)}.
     * <p>
     * It is safe to call it any number of times, it always return the same compund build on first request. After the
     * compound is built for the first time, any modifications are forbidden an throw {@link CompoundAlreadyBuiltError}.
     * This limitation should be strictly implemented. No rebuilds could be ever possible!
     * <p>
     * @return signed and ready for registration Compound performing the transfer operation.
     */
    Compound build() {
        return null;
    }
}
