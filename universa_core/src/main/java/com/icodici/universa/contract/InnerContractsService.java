package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Config;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.util.Set;

/**
 * Developer's methods for help with creating and preparing contracts.
 */
public class InnerContractsService {



    /**
     * Creates fresh contract in the first revision with "U".
     *<br><br>
     * This contract should be registered and then should be used as payment for other contract's processing.
     * U contracts signs with special Universa keys and set as owner public keys from params.
     *<br><br>
     * @param amount is initial number of U that will be have an owner
     * @param ownerKeys is public keys that will became an owner of U
     * @return sealed U contract; should be registered in the Universa by simplified procedure.
     * @throws IOException with exceptions while contract preparing
     */
    public synchronized static Contract createFreshU(int amount, Set<PublicKey> ownerKeys) throws IOException {

        return createFreshU(amount, ownerKeys, false);
    }

    /**
     * @deprecated use {@link #createFreshU(int, Set)} instead.
     */
    @Deprecated
    public synchronized static Contract createFreshTU(int amount, Set<PublicKey> ownerKeys) throws IOException {

        return createFreshTU(amount, ownerKeys, false);
    }

    /**
     * Creates fresh contract in the first revision with "U".
     *<br><br>
     * This contract should be registered and then should be used as payment for other contract's processing.
     * U contracts signs with special Universa keys and set as owner public keys from params.
     *<br><br>
     * @param amount is initial number of U that will be have an owner
     * @param ownerKeys is public keys that will became an owner of "U"
     * @param withTestU if true U will be created with test "U"
     * @return sealed U contract; should be registered in the Universa by simplified procedure.
     * @throws IOException with exceptions while contract preparing
     */
    public synchronized static Contract createFreshU(int amount, Set<PublicKey> ownerKeys, boolean withTestU) throws IOException {

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(Config.uKeyPath));
        Contract u;
        if(withTestU) {
            u = Contract.fromDslFile(Config.testUTemplatePath);
        } else {
            u = Contract.fromDslFile(Config.uTemplatePath);
        }

        SimpleRole ownerRole = new SimpleRole("owner");
        for (PublicKey k : ownerKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerRole.addKeyRecord(kr);
        }

        u.registerRole(ownerRole);
        u.createRole("owner", ownerRole);

        u.getStateData().set("transaction_units", amount);
        if(withTestU) {
            u.getStateData().set("test_transaction_units", amount * 100);
        }

        u.addSignerKey(manufacturePrivateKey);
        u.seal();

        return u;
    }

    /**
     * @deprecated use {@link #createFreshU(int, Set, boolean)} instead.
     */
    @Deprecated
    public synchronized static Contract createFreshTU(int amount, Set<PublicKey> ownerKeys, boolean withTestU) throws IOException {
        return createFreshU(amount, ownerKeys, withTestU);
    }

    /**
     * Return field from given contract as Decimal if it possible.
     *<br><br>
     * @param contract is contract from which field should be got.
     * @param fieldName is name of the field to got
     * @return field as Decimal or null.
     */
    public static Decimal getDecimalField(Contract contract, String fieldName) {

        Object valueObject = contract.getStateData().get(fieldName);

        if(valueObject instanceof Decimal) {
            return (Decimal) valueObject;
        }

        return new Decimal(valueObject.toString());
    }
}
