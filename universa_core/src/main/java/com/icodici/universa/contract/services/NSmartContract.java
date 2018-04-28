package com.icodici.universa.contract.services;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.SmartContract;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

public class NSmartContract extends SmartContract implements NContract {


    /**
     * Extract contract from v2 or v3 sealed form, getting revokein and new items from the transaction pack supplied. If
     * the transaction pack fails to resove a link, no error will be reported - not sure it's a good idea. If need, the
     * exception could be generated with the transaction pack.
     * <p>
     * It is recommended to call {@link #check()} after construction to see the errors.
     *
     * @param sealed binary sealed contract.
     * @param pack   the transaction pack to resolve dependeincise agains.
     *
     * @throws IOException on the various format errors
     */
    public NSmartContract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        super(sealed, pack);
//        getDefinition().setExtendedType(SmartContractType.N_SMART_CONTRACT.name());
    }

    public NSmartContract() {
        super();
//        getDefinition().setExtendedType(SmartContractType.N_SMART_CONTRACT.name());
    }

    /**
     * Create a default empty new contract using a provided key as issuer and owner and sealer. Default expiration is
     * set to 5 years.
     * <p>
     * This constructor adds key as sealing signature so it is ready to {@link #seal()} just after construction, thought
     * it is necessary to put real data to it first. It is allowed to change owner, expiration and data fields after
     * creation (but before sealing).
     *
     * @param key is {@link PrivateKey} for creating roles "issuer", "owner", "creator" and sign contract
     */
    public NSmartContract(PrivateKey key) {
        super(key);
        getDefinition().setExtendedType(SmartContractType.N_SMART_CONTRACT.name());
    }

    @Override
    public void onContractStorageSubscriptionEvent(ContractStorageSubscription.Event event) {

    }

    protected NSmartContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        return this;
    }

    public static SmartContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new NSmartContract().initializeWithDsl(binder);
        }
    }

    static {
        Config.forceInit(NSmartContract.class);
        DefaultBiMapper.registerClass(NSmartContract.class);
    }
}
