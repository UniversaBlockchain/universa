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
     * Creates fresh contract in the first revision with transaction units.
     *<br><br>
     * This contract should be registered and then should be used as payment for other contract's processing.
     * TU contracts signs with special Universa keys and set as owner public keys from params.
     *<br><br>
     * @param amount is initial number of TU that will be have an owner
     * @param ownerKeys is public keys that will became an owner of TU     *
     * @return sealed TU contract; should be registered in the Universa by simplified procedure.
     * @throws IOException with exceptions while contract preparing
     */
    public synchronized static Contract createFreshTU(int amount, Set<PublicKey> ownerKeys) throws IOException {

        return createFreshTU(amount, ownerKeys, false);
    }


    /**
     * Creates fresh contract in the first revision with transaction units.
     *<br><br>
     * This contract should be registered and then should be used as payment for other contract's processing.
     * TU contracts signs with special Universa keys and set as owner public keys from params.
     *<br><br>
     * @param amount is initial number of TU that will be have an owner
     * @param ownerKeys is public keys that will became an owner of TU
     * @param withTestTU if true TU will be created with test transaction units
     * @return sealed TU contract; should be registered in the Universa by simplified procedure.
     * @throws IOException with exceptions while contract preparing
     */
    public synchronized static Contract createFreshTU(int amount, Set<PublicKey> ownerKeys, boolean withTestTU) throws IOException {

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(Config.tuKeyPath));
        Contract tu;
        if(withTestTU) {
            tu = Contract.fromDslFile(Config.testTUTemplatePath);
        } else {
            tu = Contract.fromDslFile(Config.tuTemplatePath);
        }

        SimpleRole ownerRole = new SimpleRole("owner");
        for (PublicKey k : ownerKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerRole.addKeyRecord(kr);
        }

        tu.registerRole(ownerRole);
        tu.createRole("owner", ownerRole);

        tu.getStateData().set("transaction_units", amount);
        if(withTestTU) {
            tu.getStateData().set("test_transaction_units", amount * 100);
        }

        tu.addSignerKey(manufacturePrivateKey);
        tu.seal();

        return tu;
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
