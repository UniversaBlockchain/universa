package com.icodici.universa.contract.helpers;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;

import java.math.BigDecimal;
import java.util.List;

public class ManagedToken {

    interface Minting {
        /**
         * @return Data to be send to the network (in parcel if need)
         */
        byte[] getPacked();

        /**
         * @return Token that will be approved by the operation.
         */
        Contract getToken();

        /**
         * @return contract that controls current amount of minted tokens. Retrieves ot from the compound
         */
        Contract getMintingProtocol();
    }

    /**
     * Create initial supply and minting protocol:
     * @param initialAmount
     * @param ownerKeys
     * @return minting operation, that should somehow be registered with the network
     */
    static Minting create(BigDecimal initialAmount,PrivateKey ...ownerKeys) {
        throw new RuntimeException("not implemented");
    }

    /**
     * Mint more tokens. Needs minting protocol from previous operation or {@link #create(BigDecimal, PrivateKey...)}.
     *
     * @param mintingProtocol current approved protocol
     * @param amount additional minting amount
     * @param neededKeys all keys needed to mint token (as protocol require)
     * @return minting operation, that should somehow be registered with the network
     */
    static Minting mint(Contract mintingProtocol,BigDecimal amount,PrivateKey ...neededKeys) {
        throw new RuntimeException("not implemented");
    }

    /**
     * Revoke 1+ tokens (unitilze)
     * @param tokens to revoke
     * @param keys necessary for the operations
     * @return new revision of MintingProtocol contract.
     */
    static Contract revokeTokens(List<Contract> tokens,List<PrivateKey> keys) {
        throw new RuntimeException("not implemented");
    }

}
