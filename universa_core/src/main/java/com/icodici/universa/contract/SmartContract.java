package com.icodici.universa.contract;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static com.icodici.universa.Errors.BAD_VALUE;
import static com.icodici.universa.Errors.FAILED_CHECK;

@BiType(name = "UniversaSmartContract")
public class SmartContract extends Contract implements NodeSmartContract {


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
    public SmartContract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        super(sealed, pack);
        getDefinition().setExtendedType(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name());
    }

    public SmartContract() {
        super();
        getDefinition().setExtendedType(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name());
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
    public SmartContract(PrivateKey key) {
        super(key);
        getDefinition().setExtendedType(SmartContract.SmartContractType.DEFAULT_SMART_CONTRACT.name());
    }

    @Override
    public @NonNull String getExtendedType() {
        try {
            return get("definition.extended_type");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean beforeCreate(Contract c) {
        return true;
    }

    @Override
    public boolean beforeUpdate(Contract c) {
        return true;
    }

    @Override
    public boolean beforeRevoke(Contract c) {
        return true;
    }

    @Override
    public @Nullable Binder onCreated(Contract c) {
        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public @Nullable Binder onUpdate(Contract c) {
        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public void onRevoke(Contract c) {

    }

    @Override
    public @NonNull Binder query(String methodName, Binder params) {
        return null;
    }

    @Override
    public void addError(ErrorRecord r) {
        super.addError(r);
    }

    @Override
    public Binder toBinder() {
        return super.toBinder();
    }

    @Override
    public boolean check() throws Quantiser.QuantiserException {
        boolean checkResult = false;

        String extendedTypeString = getExtendedType();
        if(extendedTypeString != null) {

            SmartContractType scType = null;
            try {
                scType = SmartContractType.valueOf(extendedTypeString);
                if(scType != null) {
                    checkResult = true;
                }

            } catch (IllegalArgumentException e) {
                addError(BAD_VALUE, "definition.extended_type", "illegal value, should be string from SmartContractType enum");
                checkResult = false;
            }
        } else {
            addError(BAD_VALUE, "definition.extended_type", "value not defined, should be string from SmartContractType enum");
            checkResult = false;
        }

        if(!checkResult)
            return checkResult;

        checkResult = super.check();
        if(!checkResult) {
            return checkResult;
        }

        checkResult = beforeCreate(null);
        if(!checkResult) {
            addError(FAILED_CHECK, "beforeCreate returns false");
            return checkResult;
        }

        checkResult = beforeUpdate(null);
        if(!checkResult) {
            addError(FAILED_CHECK, "beforeUpdate returns false");
            return checkResult;
        }

        checkResult = beforeRevoke(null);
        if(!checkResult) {
            addError(FAILED_CHECK, "beforeRevoke returns false");
            return checkResult;
        }

        return checkResult;
    }

    protected SmartContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        return this;
    }

    public static SmartContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new SmartContract().initializeWithDsl(binder);
        }
    }


    public enum SmartContractType {
        DEFAULT_SMART_CONTRACT
    }

    static {
        Config.forceInit(SmartContract.class);
        DefaultBiMapper.registerClass(SmartContract.class);
    }
}
