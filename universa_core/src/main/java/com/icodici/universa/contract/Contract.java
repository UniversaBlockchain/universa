/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved  
 *  
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.*;
import com.icodici.universa.*;
import com.icodici.universa.contract.jsapi.*;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.*;
import com.icodici.universa.contract.services.FollowerContract;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.contract.services.SlotContract;
import com.icodici.universa.contract.services.UnsContract;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.Quantiser;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64u;
import net.sergeych.utils.Bytes;
import net.sergeych.utils.Ut;
import org.bouncycastle.asn1.bc.ObjectDataSequence;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import javax.script.ScriptException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.icodici.crypto.PublicKey.PUBLIC_KEY_BI_ADAPTER;
import static com.icodici.universa.Errors.*;
import static java.util.Arrays.asList;

@BiType(name = "UniversaContract")
public class Contract implements Approvable, BiSerializable, Cloneable {


    private Binder serviceContracts;
    private Set<PublicKey> votedByKeys;

    public void setVotedByKeys(Set<PublicKey> votedByKeys) {
        this.votedByKeys = votedByKeys;
    }

    public void matchReferences() {
        getQuantiser().resetNoLimit();
        HashMap contractsTree = new HashMap(getTransactionPack().getSubItems());
        contractsTree.putAll(getTransactionPack().getReferencedItems());
        contractsTree.put(getId(),this);
        try {
            checkReferencedItems();
        } catch (Quantiser.QuantiserException e) {
            e.printStackTrace();
        }
    }

    private Map<HashId, Contract> neighbourContracts;
    private Set<PublicKey> referenceContextKeys = new HashSet<>();

    public static class UnicapsuleExpectedException extends IllegalArgumentException {

        public UnicapsuleExpectedException(String message) {
            super(message);
        }
    }
    private static final int MAX_API_LEVEL = 4;
    private final Set<Contract> revokingItems = new HashSet<>();
    private final Set<Contract> newItems = new HashSet<>();
    private final Map<String, Role> roles = new HashMap<>();
    private Definition definition;
    private State state;
    private Transactional transactional;
    private byte[] sealedBinary;
    private int apiLevel = MAX_API_LEVEL;
    private Context context = null;

    private boolean shouldBeU = false;
    private boolean limitedForTestnet = false;
    private boolean isSuitableForTestnet = false;
    private boolean isNeedVerifySealedKeys = false;

    /**
     * true if the contract was imported from sealed capsule
     */
    private boolean isSealed = false;
    private final Map<PublicKey, ExtendedSignature> sealedByKeys = new HashMap<>();
    private Map<PublicKey, ExtendedSignature> effectiveKeys = new HashMap<>();
    private Set<PrivateKey> keysToSignWith = new HashSet<>();
    private HashMap<String, Reference> references = new HashMap<>();
    private HashId id;
    private TransactionPack transactionPack;
    private Set<String> validRoleReferences = new HashSet<>();
    private final List<String> predefinedRoles = Collections.unmodifiableList(Arrays.asList("creator", "owner", "issuer"));

    public Quantiser getQuantiser() {
        return quantiser;
    }

    private void setQuantiser(Quantiser newQuantiser) {
        quantiser = newQuantiser;
    }

    /**
     * Instance that keep cost of processing contract
     */
    private Quantiser quantiser = new Quantiser();

    public static int getTestQuantaLimit() {
        return testQuantaLimit;
    }

    public static void setTestQuantaLimit(int testQuantaLimit) {
        Contract.testQuantaLimit = testQuantaLimit;
    }

    private static int testQuantaLimit = -1;

    public static String JSAPI_SCRIPT_FIELD = "scripts";

    /**
     * Extract contract from v2 or v3 sealed form, getting revoking and new items from sealed unicapsule and referenced items from
     * the transaction pack supplied.
     * <p>
     * It is recommended to call {@link #check()} after construction to see the errors.
     *
     * @param sealed binary sealed contract.
     * @param pack   the transaction pack to resolve dependeincise agains.
     *
     * @throws IOException on the various format errors
     */
    public Contract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        this(Boss.unpack(sealed),pack);
        sealedBinary = sealed;
    }

    public Contract(Binder data, @NonNull TransactionPack pack) throws IOException {
        this.transactionPack = pack;

        this.getQuantiser().reset(testQuantaLimit); // debug const. need to get quantaLimit from TransactionPack here

        isNeedVerifySealedKeys = true;
        if (!data.getStringOrThrow("type").equals("unicapsule"))
            throw new UnicapsuleExpectedException("wrong object type, unicapsule required");

        apiLevel = data.getIntOrThrow("version");

        byte[] contractBytes = data.getBinaryOrThrow("data");

        // This must be explained. By default, Boss.load will apply contract transformation in place
        // as it is registered BiSerializable type, and we want to avoid it. Therefore, we decode boss
        // data without BiSerializer and then do it by hand calling deserialize:
        Binder payload = Boss.load(contractBytes, null);
        BiDeserializer bm = BossBiMapper.newDeserializer();
        deserialize(payload.getBinderOrThrow("contract"), bm);

        if (apiLevel < 3) {
            // Legacy format: revoking and new items are included (so the contract pack grows)
            for (Object packed : payload.getList("revoking", Collections.EMPTY_LIST)) {
                Contract c = new Contract(((Bytes) packed).toArray(), pack);
                revokingItems.add(c);
                pack.addSubItem(c);
            }

            for (Object packed : payload.getList("new", Collections.EMPTY_LIST)) {
                Contract c = new Contract(((Bytes) packed).toArray(), pack);
                newItems.add(c);
                pack.addSubItem(c);
            }
        } else {
            // new format: only references are included
            for (Binder b : (List<Binder>) payload.getList("revoking", Collections.EMPTY_LIST)) {
                HashId hid = HashId.withDigest(b.getBinaryOrThrow("composite3"));
                Contract r = pack.getSubItem(hid);
                if (r != null) {
                    revokingItems.add(r);
                } else {
//                    System.out.println("Revoking item was not found in the transaction pack");
                    addError(Errors.BAD_REVOKE, "Revoking item was not found in the transaction pack");
                }
            }
            for (Binder b : (List<Binder>) payload.getList("new", Collections.EMPTY_LIST)) {
                HashId hid = HashId.withDigest(b.getBinaryOrThrow("composite3"));
                Contract n = pack.getSubItem(hid);
                if (n != null) {
                    newItems.add(n);
                }else {
//                    System.out.println("New item was not found in the transaction pack");
                    addError(Errors.BAD_NEW_ITEM, "New item was not found in the transaction pack");
                }
            }
        }

        // if exist siblings for contract (more then itself)
        getContext();
        if(getSiblings().size() > 1) {
            newItems.forEach(i -> i.context = context);
        }
    }

    /**
     * Extract contract from v2 or v3 sealed form, getting revoking and new items from sealed unicapsule.
     * <p>
     * It is recommended to call {@link #check()} after construction to see the errors.
     *
     * @param data binary sealed contract.
     *
     * @throws IOException on the various format errors
     */
    public Contract(byte[] data) throws IOException {
        this(data, new TransactionPack());
        getTransactionPack().setContract(this);
    }


    /**
     * Extract old, deprecated v2 self-contained binary partially unpacked by the {@link TransactionPack}, and fill the
     * transaction pack with its contents. This contsructor also fills transaction pack instance with the new and
     * revoking items included in its body in v2.
     *
     * @param sealed binary sealed contract
     * @param data   unpacked sealed data (it is ready by the time of calling it)
     * @param pack is {@link TransactionPack} for contract was sent with
     *
     * @throws IOException on the various format errors
     */
    public Contract(byte[] sealed, Binder data, TransactionPack pack) throws IOException {
        this.transactionPack = pack;

        this.getQuantiser().reset(testQuantaLimit); // debug const. need to get quantaLimit from TransactionPack here

        this.sealedBinary = sealed;
        if (!data.getStringOrThrow("type").equals("unicapsule"))
            throw new IllegalArgumentException("wrong object type, unicapsule required");
        int v = data.getIntOrThrow("version");
        if (v > 2)
            throw new IllegalArgumentException("This constructor requires version 2, got version " + v);
        byte[] contractBytes = data.getBinaryOrThrow("data");

        // This must be explained. By default, Boss.load will apply contract transformation in place
        // as it is registered BiSerializable type, and we want to avoid it. Therefore, we decode boss
        // data without BiSerializer and then do it by hand calling deserialize:
        Binder payload = Boss.load(contractBytes, null);
        BiDeserializer bm = BossBiMapper.newDeserializer();
        deserialize(payload.getBinderOrThrow("contract"), bm);

        for (Object r : payload.getList("revoking", Collections.EMPTY_LIST)) {
            Contract c = new Contract(((Bytes) r).toArray(), pack);
            revokingItems.add(c);
            pack.addSubItem(c);
        }

        for (Object r : payload.getList("new", Collections.EMPTY_LIST)) {
            Contract c = new Contract(((Bytes) r).toArray(), pack);
            newItems.add(c);
            pack.addSubItem(c);
        }

        // if exist siblings for contract (more then itself)
        getContext();
        if(getSiblings().size() > 1) {
            newItems.forEach(i -> i.context = context);
        }

        // fill references with contracts from TransactionPack

        /*if (transactional != null && transactional.references != null) {
            for(Reference ref : transactional.references) {
                ref.setContract(this);
                references.put(ref.name, ref);
            }
        }
        if (definition != null && definition.references != null) {
            for(Reference ref : definition.references) {
                ref.setContract(this);
                references.put(ref.name, ref);
            }
        }
        if (state != null && state.references != null){
            for(Reference ref : state.references) {
                ref.setContract(this);
                references.put(ref.name, ref);
            }
        }

        for(Reference ref : getReferences().values()) {

            Set<Contract> referencedContracts = new HashSet<>(pack.getReferencedItems().values());
            for(Approvable a : getNewItems()) {
                if (a instanceof Contract) {
                    referencedContracts.add((Contract) a);
                }
            }

            for(Contract c : pack.getReferencedItems().values()) {
                if(ref.isMatchingWith(c, referencedContracts)) {
                    ref.addMatchingItem(c);
                }
            }

            for(Approvable a : getNewItems()) {
                if(a instanceof Contract) {
                    Contract c = (Contract) a;
                    if (ref.isMatchingWith(c, referencedContracts)) {
                        ref.addMatchingItem(c);
                    }
                }
            }
        }

        // fill sealedByKeys from signatures matching with roles

        HashMap<Bytes, PublicKey> keys = new HashMap<Bytes, PublicKey>();

        roles.values().forEach(role -> {
            role.getKeys().forEach(key -> keys.put(ExtendedSignature.keyId(key), key));
            role.getAnonymousIds().forEach(anonId -> {
                transactionPack.getKeysForPack().forEach(
                        key -> {
                            try {
                                if(key.matchAnonymousId(anonId.getBytes())) {
                                    keys.put(ExtendedSignature.keyId(key), key);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                );
            });
            role.getKeyAddresses().forEach(keyAddr -> {
                transactionPack.getKeysForPack().forEach(
                        key -> {
                            try {
                                if(key.isMatchingKeyAddress(keyAddr)) {
                                    keys.put(ExtendedSignature.keyId(key), key);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                );
            });
        });

        for (Object signature : (List) data.getOrThrow("signatures")) {
            byte[] s = ((Bytes) signature).toArray();
            PublicKey key = ExtendedSignature.extractPublicKey(s);
            if (key == null) {
                Bytes keyId = ExtendedSignature.extractKeyId(s);
                key = keys.get(keyId);
            }
            if (key != null) {
                verifySignatureQuantized(key);
                ExtendedSignature es = ExtendedSignature.verify(key, s, contractBytes);
                if (es != null) {
                    sealedByKeys.put(key, es);
                } else
                    addError(Errors.BAD_SIGNATURE, "keytag:" + key.info().getBase64Tag(), "the signature is broken");
            }
        }*/
    }

    /**
     * Create an empty new contract
     */
    public Contract() {
        this.getQuantiser().reset(testQuantaLimit); // debug const. need to get quantaLimit from TransactionPack here

        definition = new Definition();
        state = new State();
    }

    /**
     * Create a default empty new contract using a provided key as issuer and owner and sealer. Default expiration is
     * set to 90 days.
     * <p>
     * This constructor adds key as sealing signature so it is ready to {@link #seal()} just after construction, thought
     * it is necessary to put real data to it first. It is allowed to change owner, expiration and data fields after
     * creation (but before sealing).
     * <p>
     * Change owner permission is added by default
     * @param key is {@link PrivateKey} for creating roles "issuer", "owner", "creator" and sign contract
     */
    public Contract(PrivateKey key) {
        this();
        // default expiration date
        setExpiresAt(ZonedDateTime.now().plusDays(90));
        // issuer role is a key for a new contract
        setIssuerKeys(key.getPublicKey());
        // issuer is owner, link roles
        addRole(new RoleLink("owner", this, "issuer"));
        addRole(new RoleLink("creator", this,"issuer"));
        RoleLink roleLink = new RoleLink("@change_owner_role",this,"owner");
        // owner can change permission
        addPermission(new ChangeOwnerPermission(roleLink));
        // issuer should sign
        addSignerKey(key);
    }

    /**
     * Get list of errors found during contract check
     * @return lsit of {@link ErrorRecord} containg errors found
     */
    public List<ErrorRecord> getErrors() {
        return errors;
    }

    private final List<ErrorRecord> errors = new ArrayList<>();

    protected Contract initializeWithDsl(Binder root) throws EncryptionError {
        apiLevel = root.getIntOrThrow("api_level");
        definition = new Definition().initializeWithDsl(root.getBinder("definition"));
        state = new State().initializeWithDsl(root.getBinder("state"));

        // fill references list
        if (definition != null && definition.references != null) {
            for(Reference ref : definition.references) {
                ref.setContract(this);
                references.put(ref.name, ref);
            }
        }

        if (state != null && state.references != null){
            for(Reference ref : state.references) {
                ref.setContract(this);
                references.put(ref.name, ref);
            }
        }

        // now we have all roles, we can build permissions:
        definition.scanDslPermissions();
        return this;
    }

    /**
     * Create contract importing its parameters with passed .yaml file. No signatures are added automatically. It is required to add signatures before check.
     * @param fileName path to file containing Yaml representation of contract.
     */
    public static Contract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new Contract().initializeWithDsl(binder);
        }
    }

    /**
     * Create contract importing its parameters with passed yaml string. No signatures are added automatically. It is required to add signatures before check.
     * @param dslString containing Yaml representation of contract
     */
    public static Contract fromDslString(String dslString) throws IOException {
        Yaml yaml = new Yaml();
        Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(dslString)));
        return new Contract().initializeWithDsl(binder);
    }

    /**
     * Create contract importing its parameters with passed yaml stream. No signatures are added automatically. It is required to add signatures before check.
     * @param dslStream containing Yaml representation of contract
     */
    public static Contract fromDslStream(InputStream dslStream) throws IOException {
        Yaml yaml = new Yaml();
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(dslStream)));
            return new Contract().initializeWithDsl(binder);
    }

    /**
     * Get state of a contract
     * @return object containing contract state
     */
    public State getState() {
        return state;
    }

    /**
     * @return {@link Transactional} object containing contract transacitonal.
     */
    public Transactional getTransactional() {
        return transactional;
    }

    /**
     * Get current api level of the contract
     * @return api level of the contract.
     */
    public int getApiLevel() {
        return apiLevel;
    }

    /**
     * Set  api level of the contract
     * @param  apiLevel api level to be set
     */
    public void setApiLevel(int apiLevel) {
        this.apiLevel = apiLevel;
    }


    /**
     * Get map contract's references
     * @return contract's references
     */
    @Override
    public HashMap<String, Reference> getReferences() {
        return references;
    }

    /**
     * Get contracts that match all the reference
     * @return referenced items
     */
    @Override
    public Set<Approvable> getReferencedItems() {

        Set<Approvable> referencedItems = new HashSet<>();
        if (transactional != null && transactional.getReferences() != null) {
            for (Reference r : transactional.getReferences()) {
                referencedItems.addAll(r.matchingItems);
            }
        }
        if (definition != null && definition.getReferences() != null) {
            for (Reference r : definition.getReferences()) {
                referencedItems.addAll(r.matchingItems);
            }
        }
        if (state != null && state.getReferences() != null) {
            for (Reference r : state.getReferences()) {
                referencedItems.addAll(r.matchingItems);
            }
        }
        return referencedItems;
    }


    public void removeReferencedItem(Contract removed) {

        for (Reference ref: getReferences().values())
            ref.matchingItems.remove(removed);

        if (transactional != null && transactional.references != null)
            for (Reference ref: transactional.references)
                ref.matchingItems.remove(removed);

        if (definition != null && definition.references != null)
            for(Reference ref : definition.references)
                ref.matchingItems.remove(removed);

        if (state != null && state.references != null)
            for(Reference ref : state.references)
                ref.matchingItems.remove(removed);

        newItems.remove(removed);
        revokingItems.remove(removed);
    }

    public void removeAllReferencedItems() {
        for (Contract c: getReferenced())
            removeReferencedItem(c);
    }
    /**
     * Get contracts to be revoked within current contract registration. Revoking items require to be either parent of current contract
     * or have special {@link RevokePermission} that is allowed for keys contract is signed with
     * @return revoking items
     */
    @Override
    public Set<Approvable> getRevokingItems() {
        return (Set) revokingItems;
    }

    /**
     * Get contracts to be registered within current contract registration.
     * @return revoking items
     */
    @Override
    public Set<Approvable> getNewItems() {
        return (Set) newItems;
    }

    /**
     * Get all contracts involved in current contract registration: contract itself, new items and revoking items
     * @return contracts tree
     */
    public List<Contract> getAllContractInTree() {

        List<Contract> contracts = new ArrayList<>();
        contracts.add(this);

        for (Contract c : getNew()) {
            contracts.addAll(c.getAllContractInTree());
        }

        for (Contract c : getRevoking()) {
            contracts.addAll(c.getAllContractInTree());
        }

        return contracts;
    }

    /**
     * Checks contract is set unlimited requests for a key.
     * Errors found can be accessed with {@link #getErrors()} ()}.
     *
     * @param config is current node configuration
     * @return if contract set unlimited requests for a key
     */
    public boolean isUnlimitKeyContract(Config config) {

        try {
            // check transactional
            if ((transactional == null) || (transactional.data == null) || (transactional.data.size() != 1))
                return false;

            // check revoking contract
            if ((newItems.size() != 0) || (revokingItems.size() != 1))
                return false;

            if (!getRevoking().get(0).getId().equals(getParent()))
                return false;

            // check U contracts
            if (!isU(config.getUIssuerKeys(), config.getUIssuerName()))
                return false;

            if (!getRevoking().get(0).isU(config.getUIssuerKeys(), config.getUIssuerName()))
                return false;

            // check unlimited key
            if (!transactional.data.containsKey("unlimited_key"))
                return false;
        }
        catch (Exception e) {
            return false;
        }

        try {
            // get unlimited key
            byte[] packedKey = transactional.data.getBinary("unlimited_key");
            if (packedKey == null) {
                addError(Errors.FAILED_CHECK, "", "Invalid format of key for unlimited requests");
                return false;
            }

            PublicKey key = new PublicKey(packedKey);
            if (key == null) {
                addError(Errors.FAILED_CHECK, "", "Invalid format of key for unlimited requests");
                return false;
            }
        }
        catch (Exception e) {
            addError(Errors.FAILED_CHECK, "", "Invalid format of key for unlimited requests: " + e.getMessage());
            return false;
        }

        try {
            // check payment
            int calculatedPayment = getRevoking().get(0).getStateData().getIntOrThrow("transaction_units")
                                                       - getStateData().getIntOrThrow("transaction_units");

            if (calculatedPayment != config.getRateLimitDisablingPayment()) {
                addError(Errors.FAILED_CHECK, "", "Payment for setting unlimited requests must be " + config.getRateLimitDisablingPayment() + "U");
                return false;
            }
        }
        catch (Exception e) {
            addError(Errors.FAILED_CHECK, "", "Error checking payment for setting unlimited requests: " + e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Verify signatures in sealed contract (if needed) and forms a map of sealed keys.
     * Errors found can be accessed with {@link #getErrors()}
     *
     * @param isQuantise if needed quantisation verifying signatures
     * @throws Quantiser.QuantiserException when quantas limit was reached during check
     */
    private void verifySealedKeys(boolean isQuantise) throws Quantiser.QuantiserException {

        if (sealedBinary == null)
            return;

        if (!isNeedVerifySealedKeys) {
            if (isQuantise)
                // Add key verify quanta again (we just reset quantiser)
                for (PublicKey key : sealedByKeys.keySet())
                    if (key != null)
                        verifySignatureQuantized(key);
            return;
        }

        Binder data = Boss.unpack(sealedBinary);
        if (!data.getStringOrThrow("type").equals("unicapsule"))
            throw new IllegalArgumentException("wrong object type, unicapsule required");

        byte[] contractBytes = data.getBinaryOrThrow("data");

        // fill sealedByKeys from signatures matching with roles
        HashMap<Bytes, PublicKey> keys = new HashMap<Bytes, PublicKey>();

        Consumer<Role> extractKeys = role -> {
            RoleExtractor.extractKeys(role).forEach(key -> keys.put(ExtendedSignature.keyId(key), key));
            RoleExtractor.extractAnonymousIds(role).forEach(anonId -> {
                getTransactionPack().getKeysForPack().forEach(
                        key -> {
                            try {
                                if(key.matchAnonymousId(anonId.getBytes())) {
                                    keys.put(ExtendedSignature.keyId(key), key);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                );
            });
            RoleExtractor.extractKeyAddresses(role).forEach(keyAddr -> {
                getTransactionPack().getKeysForPack().forEach(
                        key -> {
                            try {
                                if(key.isMatchingKeyAddress(keyAddr)) {
                                    keys.put(ExtendedSignature.keyId(key), key);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                );
            });

        };

        roles.values().forEach(extractKeys);
        state.roles.values().forEach(extractKeys);

        // verify signatures
        for (Object signature : (List) data.getOrThrow("signatures")) {
            byte[] s = ((Bytes) signature).toArray();

            PublicKey key = ExtendedSignature.extractPublicKey(s);
            if (key == null) {
                Bytes keyId = ExtendedSignature.extractKeyId(s);
                key = keys.get(keyId);
            }

            if (key != null) {
                if (isQuantise)
                    verifySignatureQuantized(key);

                ExtendedSignature es = ExtendedSignature.verify(key, s, contractBytes);
                if (es != null) {
                    sealedByKeys.put(key, es);
                } else
                    addError(Errors.BAD_SIGNATURE, "keytag:" + key.info().getBase64Tag(), "the signature is broken");
            }
        }

        isNeedVerifySealedKeys = false;
    }

    /**
     * Verify signatures in contract and all sub-items (if needed).
     * Errors found can be accessed in certain contract with {@link #getErrors()}
     *
     * @throws Quantiser.QuantiserException when quantas limit was reached during check
     */
    private void verifySignatures() throws Quantiser.QuantiserException {

        verifySealedKeys(true);

        // verify signatures of new items
        for (Contract c: newItems) {
            c.setQuantiser(getQuantiser());
            c.verifySignatures();
        }

        // verify signatures of revoking items
        for (Contract c: revokingItems) {
            c.setQuantiser(getQuantiser());
            c.verifySealedKeys(true);
        }
    }


    /**
     * Check contract for errors. This includes checking contract state modification, checking new items, revoke permissions and references acceptance.
     * Errors found can be accessed with {@link #getErrors()} ()}
     *
     * @param prefix is included in errors text. Used to differ errors found in contract from errors of subcontracts (revoking,new)
     * @throws Quantiser.QuantiserException when quantas limit was reached during check
     * @return if check was successful
     */
    @Override
    public boolean check(String prefix) throws Quantiser.QuantiserException {
        //clear existing errors before check
        //errors.clear();

        if (prefix.isEmpty()) {
            getQuantiser().reset(getQuantiser().getQuantaLimit());
            verifySignatures();
            Map<HashId, Contract> contractsTree = new HashMap(getTransactionPack().getSubItems());
            contractsTree.putAll(getTransactionPack().getReferencedItems());
            contractsTree.put(getId(),this);
            setEffectiveKeys(null);
            contractsTree.forEach((id,c)->c.setNeighbourContracts(contractsTree));
        }

        // Add register a version quanta (for self)
        getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);

        // quantize revoking items
        for (int i = 0; i < revokingItems.size(); i++) {
            getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_REVOKE_VERSION);
        }

        // quantize referenced items
        for (int i = 0; i < getReferences().size(); i++) {
            getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_REFERENCED_VERSION);
        }
        //After we added cost for all sig checks we must include additional signatures
        //on regular basis so it affect the check in all ways

        getTransactionPack().setReferenceContextKeys(getEffectiveKeys());

        if (transactionPack != null)
            for (Contract ri: transactionPack.getReferencedItems().values())
                ri.setQuantiser(getQuantiser());

        checkReferencedItems();

        try {
            // common check for all cases
            //            errors.clear();
            basicCheck();

            if (state.origin == null)
                checkRootContract();
            else
                checkChangedContract();
        } catch (Quantiser.QuantiserException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            addError(FAILED_CHECK, prefix, e.toString());
        }
        int index = 0;
        for (Contract c : newItems) {
            String p = prefix + "new[" + index + "].";
            checkSubItemQuantized(c, p);
            if (!c.isOk()) {
                c.errors.forEach(e -> {
                    String name = e.getObjectName();
                    name = name == null ? p : p + name;
                    addError(e.getError(), name, e.getMessage());
                });
            }
            index++;
        }
        checkDupesCreation();

        checkTestPaymentLimitations();

        return errors.size() == 0;
    }

    public void setReferenceContextKeys(Set<PublicKey> effectiveKeys) {
        validRoleReferences.clear();
        getReferences().values().forEach(r->r.matchingItems.clear());
        referenceContextKeys = effectiveKeys;
    }

    private void setNeighbourContracts(Map<HashId, Contract> neighbourContracts) {
        this.neighbourContracts = neighbourContracts;
    }


    private void setEffectiveKeys(Map<PublicKey, ExtendedSignature> additionalSignatures) {
        setEffectiveKeys(additionalSignatures,true);
    }

    private void setEffectiveKeys(Map<PublicKey, ExtendedSignature> additionalSignatures, boolean useSealedBy) {
        //TODO: if we want to filter by creator keys -> do it here. it is the best place
        effectiveKeys = useSealedBy ? new HashMap<>(sealedByKeys) : new HashMap<>();

        if(additionalSignatures != null) {
            effectiveKeys.putAll(additionalSignatures);
        }

        if(votedByKeys != null) {
            votedByKeys.forEach(k->effectiveKeys.put(k,null));
        }

        newItems.forEach(c -> c.setEffectiveKeys(effectiveKeys));
        revokingItems.forEach(c -> c.setEffectiveKeys(effectiveKeys,false));

    }

    private boolean checkReferencedItems() throws Quantiser.QuantiserException {
        return checkReferencedItems(null);
    }


    public boolean checkReferencedItems(Set<String> roleRefsToResolve) throws Quantiser.QuantiserException {

        if (getReferences().size() == 0) {
            // if contract has no references -> then it's checkReferencedItems check is ok
            return true;
        }

        Collection<Contract> neighbours = neighbourContracts == null ? new HashSet<>() : neighbourContracts.values();

        // quantize references
        for (final Reference r : getReferences().values())
            r.quantize(getQuantiser(), neighbours.size());

        // check each reference, all must be ok
        boolean allRefs_check = true;
        for (final Reference rm : getReferences().values()) {

            if(roleRefsToResolve != null) {
                if (!roleRefsToResolve.contains(rm.name))
                    continue;
                if (validRoleReferences.contains(rm.name))
                    continue;
            } else {
                boolean roleReference = roles.values().stream().anyMatch(role -> role.containReference(rm.name)) || state.roles.values().stream().anyMatch(role -> role.containReference(rm.name)) || permissions.values().stream().anyMatch(p -> p.getRole().containReference(rm.name));
                if (roleReference)
                    continue;
            }

            // use all neighbourContracts to check reference. at least one must be ok
            boolean rm_check = false;
            if(rm.type == Reference.TYPE_TRANSACTIONAL) {
                for(Contract neighbour : neighbours) {
                    if ((((rm.transactional_id != null && neighbour.transactional != null && rm.transactional_id.equals(neighbour.transactional.id)) ||
                            (rm.contract_id != null && rm.contract_id.equals(neighbour.id))) && checkOneReference(rm, neighbour)) ||
                            (rm.getConditions().size() > 0))    // new format of reference with conditions, transactional_id - optional
                        if (rm.isMatchingWithQuantized(neighbour, neighbours, getQuantiser())) {
                            rm.addMatchingItem(neighbour);
                            rm_check = true;
                            break;
                        }
                }
            } else if ((rm.type == Reference.TYPE_EXISTING_DEFINITION) || (rm.type == Reference.TYPE_EXISTING_STATE)) {

                for(Contract neighbour : neighbours) {
                    if(rm.isMatchingWithQuantized(neighbour, neighbours, getQuantiser())) {
                        rm.addMatchingItem(neighbour);
                    }
                }

                rm_check = rm.isValid();
            }

            if (rm_check == false) {
                if(roleRefsToResolve == null) {
                    allRefs_check = false;
                    addError(Errors.FAILED_CHECK, "checkReferencedItems for contract (hashId=" + getId().toString() + "): false");
                }
            } else {
                if(roleRefsToResolve != null)
                    validRoleReferences.add(rm.name);
            }
        }

        return allRefs_check;
    }

    private boolean checkOneReference(final Reference rm, final Contract refContract) throws Quantiser.QuantiserException {
        boolean res = true;

/*        if((rm.type == Reference.TYPE_EXISTING_DEFINITION) || (rm.type == Reference.TYPE_EXISTING_STATE)) {
//            res = false;
//            addError(Errors.UNKNOWN_COMMAND, "Reference.TYPE_EXISTING not implemented");
        } else */
        if (rm.type == Reference.TYPE_TRANSACTIONAL) {
            if ((rm.transactional_id == null) ||
                (refContract.transactional == null) ||
                (refContract.transactional.getId() == null) ||
                "".equals(rm.transactional_id) ||
                "".equals(refContract.transactional.id)) {
                res = false;
                addError(Errors.BAD_REF, "transactional is missing");
            } else {
                if (rm.transactional_id != null && refContract.transactional == null) {
                    res = false;
                    addError(Errors.BAD_REF, "transactional not found");
                } else if (!rm.transactional_id.equals(refContract.transactional.id)) {
                    res = false;
                    addError(Errors.BAD_REF, "transactional_id mismatch");
                }
            }
        }

        if (rm.contract_id != null) {
            if (!rm.contract_id.equals(refContract.id)) {
                res = false;
                addError(Errors.BAD_REF, "contract_id mismatch");
            }
        }

        if (rm.origin != null) {
            if (!rm.origin.equals(refContract.getOrigin())) {
                res = false;
                addError(Errors.BAD_REF, "origin mismatch");
            }
        }

        for (Role refRole : rm.signed_by) {
            if (!refContract.isSignedBy(refRole)) {
                res = false;
                addError(Errors.BAD_SIGNATURE, "fingerprint mismatch");
            }
        }

        return res;
    }

    private boolean checkTestPaymentLimitations() {
        boolean res = true;
        // we won't check U contract
        if (!shouldBeU()) {
            isSuitableForTestnet = true;
            for (PublicKey key : getEffectiveKeys()) {
                if (key != null) {
                    if (key.getBitStrength() != 2048) {
                        isSuitableForTestnet = false;
                        if (isLimitedForTestnet()) {
                            res = false;
                            addError(Errors.FORBIDDEN, "Only 2048 keys is allowed in the test payment mode.");
                        }
                    }
                }
            }

            ZonedDateTime expirationLimit = ZonedDateTime.now().plusMonths(Config.maxExpirationMonthsInTestMode);

            if (getExpiresAt().isAfter(expirationLimit)) {
                isSuitableForTestnet = false;
                if (isLimitedForTestnet()) {
                    res = false;
                    addError(Errors.FORBIDDEN, "Contracts with expiration date father then " + Config.maxExpirationMonthsInTestMode + " months from now is not allowed in the test payment mode.");
                }
            }

            for (Approvable ni : getNewItems()) {
                if (ni.getExpiresAt().isAfter(expirationLimit)) {
                    isSuitableForTestnet = false;
                    if (isLimitedForTestnet()) {
                        res = false;
                        addError(Errors.FORBIDDEN, "New items with expiration date father then " + Config.maxExpirationMonthsInTestMode + " months from now is not allowed in the test payment mode.");
                    }
                }
            }

            for (Approvable ri : getRevokingItems()) {
                if (ri.getExpiresAt().isAfter(expirationLimit)) {
                    isSuitableForTestnet = false;
                    if (isLimitedForTestnet()) {
                        res = false;
                        addError(Errors.FORBIDDEN, "Revoking items with expiration date father then " + Config.maxExpirationMonthsInTestMode + " months from now is not allowed in the test payment mode.");
                    }
                }
            }

            if (getProcessedCostU() > Config.maxCostUInTestMode) {
                isSuitableForTestnet = false;
                if (isLimitedForTestnet()) {
                    res = false;
                    addError(Errors.FORBIDDEN, "Contract can cost not more then " + Config.maxCostUInTestMode + " U in the test payment mode.");
                }
            }
        }

        return res;
    }

    /**
     * Check contract to be a valid "U" payment. This includes standard contract check and additiona payment related checks
     *
     * @param issuerKeys addresses of keys used by the network to issue "U"
     * @throws Quantiser.QuantiserException when quantas limit was reached during check
     * @return if check was successful
     */

    @Override
    public boolean paymentCheck(Set<KeyAddress> issuerKeys) throws Quantiser.QuantiserException {
        boolean res = true;

        boolean hasTestU = getStateData().get("test_transaction_units") != null;

        // Checks that there is a payment contract and the payment should be >= 1
        int u = getStateData().getInt("transaction_units", -1);
        int test_u = getStateData().getInt("test_transaction_units", -1);
        if (u < 0) {
            res = false;
            addError(Errors.BAD_VALUE, "u < 0");
        }

        // check valid name/type fields combination
        Object o = getStateData().get("transaction_units");
        if (o == null || o.getClass() != Integer.class) {
            res = false;
            addError(Errors.BAD_VALUE, "u name/type mismatch");
        }

        if(hasTestU) {
            o = getStateData().get("test_transaction_units");
            if (o == null || o.getClass() != Integer.class) {
                res = false;
                addError(Errors.BAD_VALUE, "test_u name/type mismatch");
            }

            if (test_u < 0) {
                res = false;
                addError(Errors.BAD_VALUE, "test_u < 0");
            }

            if(state.origin != null) {
                getContext();
                Contract parent;
                // if exist siblings for contract (more then itself)
                if (getSiblings().size() > 1) {
                    parent = getContext().base;
                } else {
                    parent = getRevokingItem(getParent());
                }
                int was_u = parent.getStateData().getInt("transaction_units", -1);
                int was_test_u = parent.getStateData().getInt("test_transaction_units", -1);

                if (u != was_u && test_u != was_test_u) {
                    res = false;
                    addError(Errors.BAD_VALUE, "u and test_u can not be spent both");
                }
//                if(isLimitedForTestnet()) {
//                    if (was_test_u - test_u > 3) {
//                        res = false;
//                        addError(Errors.BAD_VALUE, "Cannot spend more then 3 test_u in the test payment mode.");
//                    }
//                }
            } else {
                if(isLimitedForTestnet()) {
                    res = false;
                    addError(Errors.BAD_VALUE, "Payment contract has not origin but it is not allowed for parcel. Use standalone register for payment contract.");
                }
            }
        } else {
            if(isLimitedForTestnet()) {
                res = false;
                addError(Errors.BAD_VALUE, "Payment contract that marked as for testnet has not test_u.");
            }
        }

        // check valid decrement_permission
        if (!isPermitted("decrement_permission", getSealedByKeys())) {
            res = false;
            addError(Errors.BAD_VALUE, "decrement_permission is missing");
        }

        // The "U" contract is checked to have valid issuer key (one of preset URS keys)

        Role issuer = getIssuer();
        if(!(issuer instanceof SimpleRole)) {
            res = false;
            addError(Errors.BAD_VALUE, "issuer is not valid. must be simple role");
        } else {
            Set<KeyAddress> thisIssuerAddresses = new HashSet<>(((SimpleRole) issuer).getSimpleKeyAddresses());
            for (PublicKey publicKey : ((SimpleRole) issuer).getSimpleKeys())
                thisIssuerAddresses.add(publicKey.getShortAddress());


            if (Collections.disjoint(issuerKeys, thisIssuerAddresses)) {
                res = false;
                addError(Errors.BAD_VALUE, "issuerKeys is not valid");
            }
        }

        // If the check is failed, checking process is aborting
        if (!res) {
            return res;
        }

        // The U shouldn't have any new items
        if (newItems.size() > 0) {
            res = false;
            addError(Errors.BAD_NEW_ITEM, "payment contract can not have any new items");
        }

        // If the check is failed, checking process is aborting
        if (!res) {
            return res;
        }

        // check if payment contract not origin itself, means has revision more then 1
        // don't make this check for initial u contract
        if ((getRevision() != 1) || (getParent()!=null)) {
            if (getOrigin().equals(getId())) {
                res = false;
                addError(Errors.BAD_VALUE, "can't origin itself");
            }
            if (getRevision() <= 1) {
                res = false;
                addError(Errors.BAD_VALUE, "revision must be greater than 1");
            }

            // The "U" is checked for its parent validness, it should be in the revoking items
            if (revokingItems.size() != 1) {
                res = false;
                addError(Errors.BAD_REVOKE, "revokingItems.size != 1");
            } else {
                Contract revoking = revokingItems.iterator().next();
                if (!revoking.getOrigin().equals(getOrigin())) {
                    res = false;
                    addError(Errors.BAD_REVOKE, "origin mismatch");
                }
            }
        }

        if (!res)
            return res;
        else
            res = check("");

        return res;
    }

    /**
     * Get the cost used for contract processing.
     * You must first call {@link #check()} method to check contract and calculate cost.
     * @return cost in quantas
     */
    public int getProcessedCost() {
        return getQuantiser().getQuantaSum();
    }

    /**
     * Get the cost used for contract processing.
     * You must first call {@link #check()} method to check contract and calculate cost.
     * @return cost in "U"
     */
    public int getProcessedCostU() {
        return (int) Math.ceil( (double) getQuantiser().getQuantaSum() / Quantiser.quantaPerU);
    }

    /**
     * @deprecated use {@link #getProcessedCostU()} instead.
     */
    @Deprecated
    public int getProcessedCostTU() {
        return getProcessedCostU();
    }

    /**
     * All new items and self must have uniqie identication for its level, e.g. origin + revision + branch should always
     * ve different.
     */
    private void checkDupesCreation() {
        if (newItems.isEmpty())
            return;
        Set<String> revisionIds = new HashSet<>();
        revisionIds.add(getRevisionId());
        int count = 0;
        for (Contract c : newItems) {
            String i = c.getRevisionId();
            if (revisionIds.contains(i)) {
                addError(Errors.BAD_VALUE, "new[" + count + "]", "duplicated revision id: " + i);
            } else
                revisionIds.add(i);
            count++;
        }
    }

    /**
     * Gets complex "id" of current revision
     * @return revision "id" in format ORIGIN/PARENT/REVISION/BRANCH
     */
    public String getRevisionId() {
        String parentId = getParent() == null ? "" : (getParent().toBase64String() + "/");
        StringBuilder sb = new StringBuilder(getOrigin().toBase64String() + "/" + parentId + state.revision);
        if (state.branchId != null)
            sb.append("/" + state.branchId.toString());
        return sb.toString();
    }

    /**
     * Create new root contract to be created. It may have parent, but does not have origin, as it is an origin itself.
     */
    private void checkRootContract() throws Quantiser.QuantiserException {
        // root contract must be issued ny the issuer
        Role issuer = getRole("issuer");
        if (issuer == null || !issuer.isValid()) {
            addError(BAD_VALUE, "definition.issuer", "missing issuer");
            return;
        }
        // the bad case - no issuer - should be processed normally without exceptions:
        Role createdBy = getRole("creator");
        if (createdBy == null || !createdBy.isValid()) {
            addError(BAD_VALUE, "state.created_by", "invalid creator");
            return;
        }


        if (!issuer.isAllowedForKeysQuantized(getEffectiveKeys())) {
            addError(ISSUER_MUST_CREATE, "issuer.keys");
        }

        Role targetRole = issuer;
        while(true) {
            if(isMutableRole(targetRole)) {
                addError(BAD_VALUE, "issuer","issuer can be modified");
                break;
            }

            if(targetRole instanceof RoleLink) {
                targetRole = ((RoleLink) targetRole).getRole();
            } else {
                break;
            }
        }






        if (state.revision != 1)
            addError(BAD_VALUE, "state.revision", "must be 1 in a root contract");
        if (state.createdAt == null)
            state.createdAt = definition.createdAt;
        else if (!state.createdAt.equals(definition.createdAt))
            addError(BAD_VALUE, "state.created_at", "invalid");
        if (state.origin != null)
            addError(BAD_VALUE, "state.origin", "must be empty in a root contract");

        if (state.parent != null)
            addError(BAD_VALUE, "state.parent", "must be empty in a root contract");

        checkRevokePermissions(revokingItems);
    }

    private boolean isMutableRole(Role role) {
        if(role.getName().equals("creator")) {
            return true;
        } else if(role.getName().equals("owner")) {
            Collection<Permission> chown = getPermissions().get("change_owner");
            if(chown != null && chown.size() > 0) {
                return true;
            }
        } else if(!role.getName().equals("issuer")) {
            Collection<Permission> chrole = getPermissions().get("change_role");
            if(chrole != null && chrole.stream().anyMatch(p->p.getParams().getString("role_name","").equals(role.getName()))) {
                return true;
            }
        }

        Collection<Permission> mdps = getPermissions().get("modify_data");
        boolean stateReferencesMutable = mdps != null && mdps.stream().anyMatch(p->p.getParams().getBinder("fields",new Binder()).containsKey("/references"));

        return getReferences().values().stream().anyMatch(r->(r.type == Reference.TYPE_TRANSACTIONAL ||
                (r.type == Reference.TYPE_EXISTING_STATE && stateReferencesMutable)) && role.containReference(r.name));

    }

    private void checkRevokePermissions(Set<Contract> revokes) throws Quantiser.QuantiserException {
        for (Contract rc : revokes) {

            //check if revoking parent => no permission is needed
            if(getParent() != null && rc.getId().equals(getParent()))
                continue;

            if (!rc.isPermitted("revoke", getEffectiveKeys()))
                addError(FORBIDDEN, "revokingItem", "revocation not permitted for item " + rc.getId());
        }
    }

    /**
     * Add error to contract
     * @param code error code
     * @param field path to contract field error found in
     * @param text error message
     */

    @Override
    public void addError(Errors code, String field, String text) {
        Errors code1 = code;
        String field1 = field;
        String text1 = text;
        errors.add(new ErrorRecord(code1, field1, text1));
    }
    /**
     * Add error to contract
     * @param er error record containing code, erroneous object and message
     */
    @Override
    public void addError(ErrorRecord er) {
        errors.add(er);
    }

    private void checkChangedContract() throws Quantiser.QuantiserException {
        // get context if not got yet
        getContext();
        Contract parent;
        // if exist siblings for contract (more then itself)
        if(getSiblings().size() > 1) {
            parent = getContext().base;
        } else {
            parent = getRevokingItem(getParent());
        }
        if (parent == null) {
            addError(BAD_REF, "parent", "parent contract must be included");
        } else {
            // checking parent:
            // proper origin
            HashId rootId = parent.getRootId();
            if (!rootId.equals(getRawOrigin())) {
                addError(BAD_VALUE, "state.origin", "wrong origin, should be root");
            }
            if (!getParent().equals(parent.getId()))
                addError(BAD_VALUE, "state.parent", "illegal parent references");

            ContractDelta delta = new ContractDelta(parent, this);
            delta.check();

            checkRevokePermissions(delta.getRevokingItems());
        }
    }

    /**
     * Get the root id with the proper logic: for the root contract, it returns its {@link #getId()}, for the child
     * contracts - the origin id (state.origin field value). Note that all contract states in the chain share same
     * origin - it is an id of the root contract, whose id is always null.
     *
     * @return id of the root contract.
     */
    protected HashId getRootId() {
        HashId origin = getRawOrigin();
        return origin == null ? getId() : origin;
    }

    /**
     * Try to find revoking item with a given ID. If the matching item exists but is not a {@link Contract} instance, it
     * will not be found, null will be returned.
     *
     * @param id to find
     *
     * @return matching Contract instance or null if not found.
     */
    protected Contract getRevokingItem(HashId id) {
        for (Approvable a : revokingItems) {
            if (a.getId().equals(id) && a instanceof Contract)
                return (Contract) a;
        }
        return null;
    }

    /**
     * Add one or more contracts to revoke. The contracts must be approved loaded from a binary. Do not call {@link
     * #seal()} on them as resealing discards network approval by changing the id!
     *
     * @param toRevoke is comma-separated contract to revoke
     */
    public void addRevokingItems(Contract... toRevoke) {
        for (Contract c : toRevoke) {
            revokingItems.add(c);
        }
    }

    private void basicCheck() throws Quantiser.QuantiserException {

        if ((transactional != null) && (transactional.validUntil != null)) {
            if (Ut.getTime(transactional.validUntil).isBefore(ZonedDateTime.now()))
                addError(BAD_VALUE, "transactional.valid_until", "time for register is over");
            else if (Ut.getTime(transactional.validUntil).isBefore(ZonedDateTime.now().plusSeconds(Config.validUntilTailTime.getSeconds())))
                addError(BAD_VALUE, "transactional.valid_until", "time for register ends");
        }

        if (definition.createdAt == null) {
            addError(BAD_VALUE, "definition.created_at", "invalid");
        }

        if(state.origin == null){
            if (definition.createdAt.isAfter(ZonedDateTime.now()) ||
                    definition.createdAt.isBefore(getEarliestCreationTime())) {
                addError(BAD_VALUE, "definition.created_at", "invalid");
            }
        }

        boolean stateExpiredAt = state.expiresAt == null || state.expiresAt.isBefore(ZonedDateTime.now());
        boolean definitionExpiredAt = definition.expiresAt == null || definition.expiresAt.isBefore(ZonedDateTime.now());

        if (stateExpiredAt) {
            if (definitionExpiredAt) {
                addError(EXPIRED, "state.expires_at");
            }
        }

        if (state.createdAt == null ||
                state.createdAt.isAfter(ZonedDateTime.now()) ||
                state.createdAt.isBefore(getEarliestCreationTime())) {
            addError(BAD_VALUE, "state.created_at");
        }
        if (apiLevel < 1)
            addError(BAD_VALUE, "api_level");
        Role owner = getRole("owner");
        if (owner == null || !owner.isValid())
            addError(MISSING_OWNER, "state.owner");
        Role issuer = getRole("issuer");
        if (issuer == null || !issuer.isValid())
            addError(MISSING_ISSUER, "state.issuer");
        if (state.revision < 1)
            addError(BAD_VALUE, "state.revision");
        Role createdBy = getRole("creator");
        if (createdBy == null || !createdBy.isValid())
            addError(BAD_VALUE, "state.created_by");
        if (!isSignedBy(createdBy)) {
            addError(NOT_SIGNED, "", "missing creator signature(s)");
        }
    }

    private boolean isSignedBy(Role role) throws Quantiser.QuantiserException {
        if (role == null)
            return false;
        if(role instanceof RoleLink)
            role = ((RoleLink)role).resolve(false);

        if (role == null)
            return false;

        return role.isAllowedForKeysQuantized(getEffectiveKeys());

    }

    /**
     * Resolve object describing role and create either: - new role object - symlink to named role instance, ensure it
     * is register and return it, if it is a Map, tries to construct and register {@link Role} then return it.
     *
     * @param roleName is name of the role
     * @param roleObject is object for role creating
     *
     * @return created {@link Role}
     */
    @NonNull
    @Deprecated
    public Role createRole(String roleName, Object roleObject) {
        if (roleObject instanceof CharSequence) {
            return registerRole(new RoleLink(roleName, this,roleObject.toString()));
        }
        if (roleObject instanceof Role)
            if(((Role)roleObject).getName() != null && ((Role)roleObject).getName().equals(roleName))
                return registerRole(((Role) roleObject));
            else
                return registerRole(((Role) roleObject).linkAs(roleName));
        if (roleObject instanceof Map) {
            Role r = Role.fromDslBinder(roleName, Binder.from(roleObject));
            return registerRole(r);
        }
        throw new IllegalArgumentException("cant make role from " + roleObject);
    }

    /**
     * Resolve object describing role and create either: - new role object - symlink to named role instance, ensure it
     * is added as persistent named role to a contract and return it.
     *
     * @param roleName is name of the role
     * @param roleObject is object for role creating
     *
     * @return created {@link Role}
     */
    @NonNull
    public Role addRole(String roleName, Object roleObject) {
        return createRole(roleName,roleObject,true);
    }

    /**
     * Resolve object describing role and create either: - new role object - symlink to named role instance return it.
     *
     * @param roleName is name of the role
     * @param roleObject is object for role creating
     * @param addToContract flag indicating if constructed role should also be added to a contract as persistent named role
     *
     * @return created {@link Role}
     */
    public Role createRole(String roleName, Object roleObject,boolean addToContract) {
        Role result = null;
        if (roleObject instanceof CharSequence) {
            result = new RoleLink(roleName, this,roleObject.toString());
        }
        if (roleObject instanceof Role)
            if(((Role)roleObject).getName() != null && ((Role)roleObject).getName().equals(roleName))
                result = (Role) roleObject;
            else
                result = ((Role) roleObject).linkAs(roleName);
        if (roleObject instanceof Map) {
            Role r = Role.fromDslBinder(roleName, Binder.from(roleObject));
            r.setContract(this);
            result = r;
        }
        if(result == null)
            throw new IllegalArgumentException("cant make role from " + roleObject);
        result.setContract(this);
        if(addToContract)
            addRole(result);
        return result;
    }


    /**
     * Get registered role from the contract
     * @param roleName name of the role to get
     * @return role or null if not exist
     */
    public Role getRole(String roleName) {
        if(predefinedRoles.contains(roleName))
            return roles.get(roleName);
        else
            return state.roles.get(roleName);
    }

    /**
     * Get all addresses, used by registered role from the contract. For public keys returns addresses too.
     * @return list of strings with addresses
     */
    @Deprecated
    public List<String> getRoleAddresses(String roleName) {
        return getRole(roleName).getAllAddresses();
    }

    /**
     * Get the id sealing self if need
     *
     * @param sealAsNeed true to seal the contract if there is no {@link #getLastSealedBinary()}.
     *
     * @return contract id.
     */
    public HashId getId(boolean sealAsNeed) {
        if (id != null)
            return id;
        if (getLastSealedBinary() == null && sealAsNeed)
            seal();
        return getId();
    }

    /**
     * Get the id of the contract
     *
     * @return contract id.
     */

    @Override
    public HashId getId() {
        if (id == null) {
            if (sealedBinary != null)
                id = new HashId(sealedBinary);
            else
                throw new IllegalStateException("the contract has no binary attached, no Id could be calculated");
        }
        return id;
    }

    /**
     * Get issuer of the contract
     * @return issuer role
     */

    public Role getIssuer() {
        // maybe we should cache it
        return getRole("issuer");
    }


    /**
     * Get contract creation time
     * @return contract creation time
     */

    @Override
    public ZonedDateTime getCreatedAt() {
        if(state.origin != null)
            return state.createdAt;
        return definition.createdAt;
    }

    /**
     * Get contract expiration time
     * @return contract expiration time
     */

    @Override
    public ZonedDateTime getExpiresAt() {
        return state.expiresAt != null ? state.expiresAt : definition.expiresAt;
    }

    /**
     * Get roles of the contract
     * @return roles map
     */

    public Map<String, Role> getRoles() {
        HashMap<String, Role> res = new HashMap<>(roles);
        res.putAll(state.roles);
        return Collections.unmodifiableMap(res);

    }

    /**
     * Get predifined roles of the contract (issuer,owner,creator)
     * @return roles map
     */

    public Map<String, Role> getPredefinedRoles() {
        return roles;
    }

    /**
     * Get custom roles of the contract stored in state.roles secion
     * @return roles map
     */

    public Map<String, Role> getCustomRoles() {
        return state.roles;
    }

    /**
     * Get definition of a contract
     * @return object containing contract definition
     */

    public Definition getDefinition() {
        return definition;
    }

    @Deprecated
    public KeyRecord testGetOwner() {
        return getRole("owner").getKeyRecords().iterator().next();
    }

    /**
     * Register new role. Name must be unique otherwise existing role will be overwritten
     * @param role
     * @return registered role
     * @deprecated use {@link #addRole(Role)} instead
     */

    @Deprecated
    public Role registerRole(Role role) {
        String name = role.getName();
        roles.put(name, role);
        role.setContract(this);
        return role;
    }


    /**
     * Adds new role to contract. Name must be unique otherwise existing role will be overwritten. Predefined roles: issuer, owner,
     * creator are going the their special sections inside contract while all other custom roles are going to state.roles and can also
     * be accessed by name using {@link #get(String)} method by passing "state.roles.$ROLE_NAME"
     *
     * Note: this method creates persistent named role within contract. Do not use this method for permission roles
     * as these roles should only be present inside of permission. It you want this role to be persistent named AND
     * be present within permission it is better to make a {@link RoleLink} within permission to persistent role by its name.
     *
     * @param role
     * @return role added to contract
     */
    public Role addRole(Role role) {
        String name = role.getName();

        if(predefinedRoles.contains(name)) {
            roles.put(name, role);
        } else {
            state.roles.put(name, role);
        }

        role.setContract(this);
        return role;
    }

    /**
     * Anonymize existing role. If role contains {@link PublicKey} it will be replaced with {@link AnonymousId}
     * @param roleName name of the role to anonymize
     */

    public void anonymizeRole(String roleName) {
        Role role = (predefinedRoles.contains(roleName) ? roles : state.roles).get(roleName);
        if (role != null)
            role.anonymize();
    }

    /**
     * Checks if permission of given type exists and is allowed for given key record
     * @param permissionName type of permission to check for
     * @param keyRecord key record to check permission with
     * @return permission allowed for keyRecord is found
     * @throws Quantiser.QuantiserException if quantas limit was reached during check
     */

    public boolean isPermitted(String permissionName, KeyRecord keyRecord) throws Quantiser.QuantiserException {
        return isPermitted(permissionName, keyRecord.getPublicKey());
    }

    private Set<String> permissionIds;

    public void addPermission(Permission perm) {
        if (permissionIds == null) {
            permissionIds =
                    getPermissions().values().stream()
                            .map(x -> x.getId())
                            .collect(Collectors.toSet());
        }

        // We need to assign contract-unique id
        if (perm.getId() == null) {
            while (true) {
                String id = Ut.randomString(6);
                if (!permissionIds.contains(id)) {
                    permissionIds.add(id);
                    perm.setId(id);
                    break;
                }
            }
        } else {
            if(permissionIds.contains(perm.getId()))
                throw new IllegalArgumentException("Permission with id '" + perm.getId() + "' already exists in contract");

            permissionIds.add(perm.getId());
        }

        permissions.put(perm.getName(), perm);
    }

    /**
     * Checks if permission of given type that is allowed for given key exists
     * @param permissionName type of permission to check for
     * @param key public key to check permission with
     * @return permission allowed for key is found
     * @throws Quantiser.QuantiserException if quantas limit was reached during check
     */

    public boolean isPermitted(String permissionName, PublicKey key) throws Quantiser.QuantiserException {
        Collection<Permission> cp = permissions.get(permissionName);
        if (cp != null) {
            for (Permission p : cp) {
                if (p.isAllowedForKeysQuantized(key)){
                    checkApplicablePermissionQuantized(p);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if permission of given type that is allowed for given keys exists
     * @param permissionName type of permission to check for
     * @param keys collection of keys to check with
     * @return permission allowed for keys is found
     * @throws Quantiser.QuantiserException if quantas limit was reached during check
     */

    public boolean isPermitted(String permissionName, Collection<PublicKey> keys) throws Quantiser.QuantiserException {
        Collection<Permission> cp = permissions.get(permissionName);
        if (cp != null) {
            for (Permission p : cp) {
                if (p.isAllowedFor(keys)) {
                    checkApplicablePermissionQuantized(p);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Add error with empty message to contract
     * @param code error code
     * @param field path to contract field error found in
     */

    protected void addError(Errors code, String field) {
        Errors code1 = code;
        String field1 = field;
        errors.add(new ErrorRecord(code1, field1, ""));
    }

    /**
     * Get earliest possible creation time of a contract. Network will check if contract being registered was created after earliest time
     * @return earliest creation time
     */
    public ChronoZonedDateTime<?> getEarliestCreationTime() {
        return ZonedDateTime.now().minusDays(10);
    }


    /**
     * Get set of public keys contract binary signed with
     * @return keys contract binary signed with
     */
    public Set<PublicKey> getSealedByKeys() {
        try {
            verifySealedKeys(false);
        }
        catch (Quantiser.QuantiserException e) {
            // not passed without Quantisation
            e.printStackTrace();
        }

        return sealedByKeys.keySet();
    }

    /**
     * Get private keys contract binary to be signed with when sealed next time. It is called before seal()
     * @return keys contract binary to be signed with
     */
    public Set<PrivateKey> getKeysToSignWith() {
        return keysToSignWith;
    }

    /**
     * Set private keys contract binary to be signed with when sealed next time. It is called before seal()
     * @param keysToSignWith key contract binary to be signed with
     */
    public void setKeysToSignWith(Set<PrivateKey> keysToSignWith) {
        this.keysToSignWith = keysToSignWith;
    }

    /**
     * Add private key from file to keys contract binary to be signed with when sealed next time. It is called before seal()
     * @param fileName path to file containing private key
     */
    public void addSignerKeyFromFile(String fileName) throws IOException {
        addSignerKey(new PrivateKey(Do.read(fileName)));
    }

    /**
     * Add private key to keys contract binary to be signed with when sealed next time. It is called before seal()
     * @param privateKey key to sign with
     */
    public void addSignerKey(PrivateKey privateKey) {
        keysToSignWith.add(privateKey);
    }

    /**
     * Add collection of private keys to keys contract binary to be signed with when sealed next time. It is called before seal()
     * @param keys key to sign with
     */
    public void addSignerKeys(Collection<PrivateKey> keys) {
        keys.forEach(k -> keysToSignWith.add(k));
    }

    /**
     * Add reference to the references list of the contract.
     *
     * Note: section of contract reference to be added to depends on {@link Reference#type}
     *
     *
     * @param reference reference to add
     */
    public void addReference(Reference reference) {
        if(reference.type == Reference.TYPE_TRANSACTIONAL) {
            if(transactional == null)
                createTransactionalSection();
            transactional.addReference(reference);
        } else if (reference.type == Reference.TYPE_EXISTING_DEFINITION)
            definition.addReference(reference);
        else if(reference.type == Reference.TYPE_EXISTING_STATE)
            state.addReference(reference);

        references.put(reference.name, reference);
    }

    /**
     * Remove reference to the references list of the contract
     * @param reference reference to remove
     */

    public void removeReference(Reference reference) {
        reference.matchingItems.forEach(approvable -> {
            if(approvable instanceof Contract) {
                newItems.remove((Contract) approvable);
                revokingItems.remove((Contract) approvable);
            }
        });

        if(reference.type == Reference.TYPE_TRANSACTIONAL) {
            if(transactional != null)
                transactional.removeReference(reference);
        } else if (reference.type == Reference.TYPE_EXISTING_DEFINITION)
            definition.removeReference(reference);
        else if(reference.type == Reference.TYPE_EXISTING_STATE)
            state.removeReference(reference);

        references.remove(reference.name);
    }


    /**
     * Important. This method should be invoked after {@link #check()}.
     *
     * @return true if there are no errors detected by now
     */
    public boolean isOk() {
        return errors.isEmpty();
    }

    public byte[] sealAsV2() {
        byte[] theContract = Boss.pack(
                BossBiMapper.serialize(
                        Binder.of(
                                "contract", this,
                                "revoking", revokingItems.stream()
                                        .map(i -> i.getLastSealedBinary())
                                        .collect(Collectors.toList()),
                                "new", newItems.stream()
                                        .map(i -> i.seal())
                                        .collect(Collectors.toList())
                        )
                )
        );
        //redundand code. already executed here newItems.stream().map(i -> i.seal())
        //newItems.forEach(c -> c.seal());
        Binder result = Binder.of(
                "type", "unicapsule",
                "version", 2,
                "data", theContract
        );
        List<byte[]> signatures = new ArrayList<>();
        keysToSignWith.forEach(key -> {
            signatures.add(ExtendedSignature.sign(key, theContract));
        });
        result.put("signatures", signatures);
        setOwnBinary(result);
        return sealedBinary;
    }

    /**
     * Seal contract to binary. This call adds signatures from {@link #getKeysToSignWith()}
     * @return contract's sealed unicapsule
     */
    public byte[] seal() {
        Object forPack = BossBiMapper.serialize(
                Binder.of(
                        "contract", this,
                        "revoking", revokingItems.stream()
                                .map(i -> i.getId())
                                .collect(Collectors.toList()),
                        "new", newItems.stream()
                                .map(i -> i.getId(true))
                                .collect(Collectors.toList())
                )
        );
        byte[] theContract = Boss.pack(
                forPack
        );
        List<byte[]> signatures = new ArrayList<>();
        Binder result = Binder.of(
                "type", "unicapsule",
                "version", 3,
                "data", theContract,
                "signatures", signatures
        );
        setOwnBinary(result);

        addSignatureToSeal(keysToSignWith);

        //add service contracts of the network attached to as service tags
        if(serviceContracts != null) {
            serviceContracts.keySet().forEach(k-> {
                try {
                    Contract c = Contract.fromSealedBinary(serviceContracts.getBinary(k));
                    getTransactionPack().addReferencedItem(c);
                    getTransactionPack().addTag(TransactionPack.TAG_PREFIX_RESERVED+k,c.getId());
                } catch (IOException ignored) {

                }
            });

        }

        return sealedBinary;
    }

    /**
     * Add signature to sealed (before) contract. Do not deserializing or changing contract bytes,
     * but will change sealed and hashId.
     *
     * Useful if you got contracts from third-party (another computer) and need to sign it.
     * F.e. contracts that should be sign with two persons.
     *
     * @param privateKey - key to sign contract will with
     */
    public void addSignatureToSeal(PrivateKey privateKey) {
        Set<PrivateKey> keys = new HashSet<>();
        keys.add(privateKey);
        addSignatureToSeal(keys);
    }

    /**
     * Add signature to sealed (before) contract. Do not deserializing or changing contract bytes,
     * but will change sealed and hashId.
     *
     * Useful if you got contracts from third-party (another computer) and need to sign it.
     * F.e. contracts that should be sign with two persons.
     *
     * @param privateKeys - key to sign contract will with
     */
    public void addSignatureToSeal(Set<PrivateKey> privateKeys) {
        if (sealedBinary == null)
            throw new IllegalStateException("failed to add signature: sealed binary does not exist");

        keysToSignWith.addAll(privateKeys);

        Binder data = Boss.unpack(sealedBinary);
        byte[] contractBytes = data.getBinaryOrThrow("data");

        for (PrivateKey key : privateKeys) {
            byte[] signature = ExtendedSignature.sign(key, contractBytes);
            addSignatureToSeal(signature,key.getPublicKey());
        }
    }


    /**
     * Add signature to sealed (before) contract. Do not deserializing or changing contract bytes,
     * but will change sealed and hashId.
     *
     * Useful if you got contracts from third-party (another computer) and need to sign it.
     * F.e. contracts that should be sign with two persons.
     *
     * @param signature - signature to add
     * @param publicKey - key signed with
     */
    public void addSignatureToSeal(byte[] signature, PublicKey publicKey) {
        if (sealedBinary == null)
            throw new IllegalStateException("failed to add signature: sealed binary does not exist");

        Binder data = Boss.unpack(sealedBinary);

        byte[] contractBytes = data.getBinaryOrThrow("data");
        List<byte[]> signatures = data.getListOrThrow("signatures");

        signatures.add(signature);
        data.put("signatures", signatures);

        ExtendedSignature es = ExtendedSignature.verify(publicKey, signature, contractBytes);
        if (es != null) {
            sealedByKeys.put(publicKey, es);
        }

        setOwnBinary(data);
    }

    /**
     * Get contract bytes from sealed (before) contract.
     *
     * Useful if you want to prepare extended signature and add it with {@link #addSignatureToSeal(byte[], PublicKey)}.
     *
     * @return contract bytes to be passed to {@link ExtendedSignature#createTargetSignature(PublicKey, byte[], boolean)}
     */
    public byte[] getContractBytes() {
        if (sealedBinary == null)
            throw new IllegalStateException("failed to retreive contract bytes. Seal contract.");

        Binder data = Boss.unpack(sealedBinary);
        return data.getBinaryOrThrow("data");
    }


    /**
     * Remove all signatures from sealed binary
     */
    public void removeAllSignatures() {
        if (sealedBinary == null)
            throw new IllegalStateException("failed to add signature: sealed binary does not exist");
        Binder data = Boss.unpack(sealedBinary);
        List<byte[]> signatures = new ArrayList<>();
        data.put("signatures", signatures);
        sealedByKeys.clear();

        setOwnBinary(data);
    }

    /**
     * Checks if signature present in sealed binary
     * @param publicKey public key to check signature of
     * @return if signature present
     * @throws Quantiser.QuantiserException if quantas limit was reached during check
     */
    public boolean findSignatureInSeal(PublicKey publicKey) throws Quantiser.QuantiserException {
        if (sealedBinary == null)
            throw new IllegalStateException("failed to create revision");
        Binder data = Boss.unpack(sealedBinary);
        byte[] contractBytes = data.getBinaryOrThrow("data");
        List<Bytes> signatures = data.getListOrThrow("signatures");
        for (Bytes s : signatures) {
            verifySignatureQuantized(publicKey);
            if (ExtendedSignature.verify(publicKey, s.getData(), contractBytes) != null)
                return true;
        }
        return false;
    }

    /**
     * Extract contract binary from sealed unicapsule
     * @return contract binary
     */

    public byte[] extractTheContract() {
        if (sealedBinary == null)
            throw new IllegalStateException("failed to create revision");
        Binder data = Boss.unpack(sealedBinary);
        byte[] contractBytes = data.getBinaryOrThrow("data");
        return contractBytes;
    }

    /**
     * Get the last knwon packed representation pf the contract. Should be called if the contract was contructed from a
     * packed binary ({@link #Contract(byte[])} or was explicitly sealed {@link #seal()}.
     * <p>
     * Caution. This method could return out of date binary, if the contract was changed after the {@link #seal()} call.
     * Before we will add track of changes, use it only if you are sure that {@link #seal()} was called and contract was
     * not changed since then.
     *
     * @return last result of {@link #seal()} call, or the binary from which was constructed.
     */
    public byte[] getLastSealedBinary() {
        return sealedBinary;
    }

    private void setOwnBinary(Binder result) {
        if(result.getArray("signatures").isEmpty()) {
            result.put("salt",Bytes.random(12).toArray());
        } else {
            result.remove("salt");
        }
        HashId previousId = sealedBinary != null ? HashId.of(sealedBinary) : null;

        sealedBinary = Boss.pack(result);
        Collection<Contract> referencedItems = null;
        Map<String,HashId> tags = null;

        if(transactionPack != null) {
            referencedItems = new ArrayList<>(transactionPack.getReferencedItems().values());
            tags = new HashMap<>();
            for(String tag : transactionPack.getTags().keySet()) {
                tags.put(tag,transactionPack.getTags().get(tag).getId());
            }
            transactionPack = null;
        }
        this.id = HashId.of(sealedBinary);

        if(referencedItems != null) {
            TransactionPack tp = getTransactionPack();
            referencedItems.forEach(ri -> tp.addReferencedItem(ri));
        }

        if(tags != null) {
            TransactionPack tp = getTransactionPack();
            tags.forEach((k,v) -> {
                //if main contract was tagged use its new it instead
                if(previousId != null && v.equals(previousId)) {
                    v = id;
                }

                tp.addTag(k,v);
            });
        }
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @return new revision of this contract, identical to this one, to be modified.
     */

    public Contract createRevision() {
        return createRevision((Transactional)null);
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param transactional is {@link Transactional} section to create new revision with
     * @return new revision of this contract, identical to this one, to be modified.
     */
    public synchronized Contract createRevision(Transactional transactional) {
        try {
            // We need to remove all matching items from
            removeAllReferencedItems();
            // We need deep copy, so, simple while not that fast.
            // note that revisions are create on clients where speed it not of big importance!
            Contract newRevision = copy();
            // modify the deep copy for a new revision
            newRevision.state.revision = state.revision + 1;
            newRevision.state.createdAt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
            newRevision.state.parent = getId();
            newRevision.state.origin = state.revision == 1 ? getId() : state.origin;
            newRevision.revokingItems.add(this);
            newRevision.transactional = transactional;

            if (newRevision.definition != null && newRevision.definition.references != null){
                for(Reference ref : newRevision.definition.references) {
                    ref.setContract(newRevision);
                    newRevision.references.put(ref.name, ref);
                }
            }
            if (newRevision.state != null && newRevision.state.references != null){
                for(Reference ref : newRevision.state.references) {
                    ref.setContract(newRevision);
                    newRevision.references.put(ref.name, ref);
                }
            }

            if(this.transactional != null && this.transactional.references != null) {
                this.transactional.references.forEach(r->newRevision.getReferences().remove(r.name));
            }

            if(transactional != null && transactional.references != null) {
                transactional.references.forEach(r->newRevision.addReference(r));
            }


            return newRevision;
        } catch (Exception e) {
            throw new IllegalStateException("failed to create revision", e);
        }
    }

    /**
     * Get current revision number of the contract
     * @return revision number
     */
    public int getRevision() {
        return state.revision;
    }

    /**
     * Get the id of the parent contract
     * @return id of the parent contract
     */

    public HashId getParent() {
        return state.parent;
    }

    /**
     * Get the id of origin contract from state.origin field. It could be null if the contract is first revision
     * @return id of the origin contract
     */

    public HashId getRawOrigin() {
        return state.origin;
    }

    /**
     * Get the id of origin contract. In case the contract is origin itself return its id.
     * @return id of the origin contract
     */

    public HashId getOrigin() {
        HashId o = state.origin;
        return o == null ? getId() : o;
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to these keys for new revision
     * @return new revision of this contract, identical to this one, to be modified.
     */
    public Contract createRevision(PrivateKey... keys) {
        return createRevision(null, keys);
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to these keys for new revision
     * @param transactional is {@link Transactional} section to create new revision with
     * @return new revision of this contract, identical to this one, to be modified.
     */
    public Contract createRevision(Transactional transactional, PrivateKey... keys) {
        return createRevision(Do.list(keys), transactional);
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to these keys for new revision
     * @return new revision of this contract, identical to this one, to be modified.
     */

    public synchronized Contract createRevision(Collection<PrivateKey> keys) {
        return createRevision(keys, null);
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to these keys for new revision
     * @param transactional is {@link Transactional} section to create new revision with
     * @return new revision of this contract, identical to this one, to be modified.
     */
    public synchronized Contract createRevision(Collection<PrivateKey> keys, Transactional transactional) {
        Contract newRevision = createRevision(transactional);
        Set<KeyRecord> krs = new HashSet<>();
        keys.forEach(k -> {
            krs.add(new KeyRecord(k.getPublicKey()));
            newRevision.addSignerKey(k);
        });
        newRevision.setCreator(krs);
        return newRevision;
    }


    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to anonymous ids of these keys
     * @return new revision of this contract, identical to this one, to be modified.
     */
    public synchronized Contract createRevisionAnonymously(Collection<?> keys) {
        return createRevisionAnonymously(keys, null);
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to anonymous ids of these keys
     * @param transactional is {@link Transactional} section to create new revision with
     * @return new revision of this contract, identical to this one, to be modified.
     */
    public synchronized Contract createRevisionAnonymously(Collection<?> keys, Transactional transactional) {
        Contract newRevision = createRevision(transactional);
        Set<AnonymousId> aids = new HashSet<>();
        AtomicBoolean returnNull = new AtomicBoolean(false);
        keys.forEach(k -> {
            if (k instanceof AbstractKey)
                aids.add(AnonymousId.fromBytes(((AbstractKey) k).createAnonymousId()));
            else if (k instanceof AnonymousId)
                aids.add((AnonymousId)k);
            else
                returnNull.set(true);
        });
        newRevision.setCreatorKeys(aids);
        if (returnNull.get())
            return null;
        return newRevision;
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to addresses of these keys
     * @return new revision of this contract, identical to this one, to be modified.
     */

    public synchronized Contract createRevisionWithAddress(Collection<?> keys) {
        return createRevisionWithAddress(keys, null);
    }

    /**
     * Create new revision to be changed, signed sealed and then ready to approve. Created "revision" contract is a copy
     * of this contract, with all fields and references correctly set. After this call one need to change mutable
     * fields, add signing keys, seal it and then apss to Universa network for approval.
     *
     * @param keys initially added and signer keys. Role "creator" is set to addresses of these keys
     * @param transactional is {@link Transactional} section to create new revision with
     * @return new revision of this contract, identical to this one, to be modified.
     */

    public synchronized Contract createRevisionWithAddress(Collection<?> keys, Transactional transactional) {
        Contract newRevision = createRevision(transactional);
        Set<KeyAddress> aids = new HashSet<>();
        AtomicBoolean returnNull = new AtomicBoolean(false);
        keys.forEach(k -> {
            if (k instanceof AbstractKey)
                aids.add(((AbstractKey) k).getPublicKey().getShortAddress());
            else if (k instanceof KeyAddress)
                aids.add((KeyAddress)k);
            else
                returnNull.set(true);
        });
        newRevision.setCreatorKeys(aids);
        if (returnNull.get())
            return null;
        return newRevision;
    }


    /**
     * Set "creator" role to given key records
     * @param records key records to set "creator" role to
     * @return creator role
     */

    public Role setCreator(Collection<KeyRecord> records) {
        return addRole(new SimpleRole("creator",this, records));
    }


    @Deprecated
    public Role setCreator(Role role) {
        return registerRole(role);
    }


    /**
     * Set "creator" role to given keys
     * @param keys keys to set "creator" role to
     * @return creator role
     */

    @NonNull
    public Role setCreatorKeys(Object... keys) {
        return setCreatorKeys(asList(keys));
    }

    /**
     * Set "creator" role to given keys
     * @param keys keys to set "creator" role to
     * @return creator role
     */
    @NonNull
    public Role setCreatorKeys(Collection<?> keys) {
        return addRole(new SimpleRole("creator",this,keys));
    }

    /**
     * Get owner role
     * @return owner role
     */
    public Role getOwner() {
        return getRole("owner");
    }

    /**
     * Set "owner" role to given key or key record
     * @param keyOrRecord key or key record to set "creator" role to
     * @return owner role
     */

    @NonNull
    public Role setOwnerKey(Object keyOrRecord) {
        return addRole(new SimpleRole("owner",this, Do.listOf(keyOrRecord)));
    }

    /**
     * Set "owner" role to given keys
     * @param keys keys to set "owner" role to
     * @return owner role
     */
    @NonNull
    public Role setOwnerKeys(Collection<?> keys) {
        return addRole(new SimpleRole("owner",this,keys));
    }

    /**
     * Set given role to given keys
     * @param roleName name of a role
     * @param keys keys to set "owner" role to
     * @return role
     */
    public Role setRoleKeys(String roleName, Object... keys) {
        return setRoleKeys(roleName,asList(keys));
    }

    /**
     * Set given role to given keys
     * @param roleName name of a role
     * @param keys keys to set "owner" role to
     * @return role
     */
    public Role setRoleKeys(String roleName, Collection<?> keys) {
        return addRole(new SimpleRole(roleName,this,keys));
    }

    /**
     * Set "owner" role to given keys
     * @param keys keys to set "owner" role to
     * @return owner role
     */
    @NonNull
    public Role setOwnerKeys(Object... keys) {
        return setOwnerKeys(asList(keys));
    }

    /**
     * Set role with given name to given keys
     * @param name role name
     * @param keys keys to set role to
     * @return registened role
     */
    @NonNull
    @Deprecated
    private Role setRole(String name, Collection keys) {
        return registerRole(new SimpleRole(name, keys));
    }

    /**
     * Get creator role
     * @return creator role
     */
    public Role getCreator() {
        return getRole("creator");
    }

    /**
     * Get contract permissions
     * @return contract permisisons
     */
    public Multimap<String, Permission> getPermissions() {
        return permissions;
    }

    /**
     * Get data section of contract state
     * @return data section of contract state
     */
    public Binder getStateData() {
        return state.getData();
    }

    /**
     * Set "issuer" role to given keys
     * @param keys keys to set "issuer" role to
     * @return issuer role
     */

    public Role setIssuerKeys(Collection<?> keys) {
        return addRole(new SimpleRole("issuer",this,keys));
    }

    /**
     * Set "issuer" role to given keys
     * @param keys keys to set "issuer" role to
     * @return issuer role
     */

    public Role setIssuerKeys(Object... keys) {
        return setIssuerKeys(asList(keys));
    }

    /**
     * Set expiration date of contract
     * @param dateTime expiration date to set
     */

    public void setExpiresAt(ZonedDateTime dateTime) {
        state.setExpiresAt(dateTime);
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        int l = data.getIntOrThrow("api_level");
        if (l > MAX_API_LEVEL)
            throw new RuntimeException("contract api level conflict: found " + l + " my level " + apiLevel);
        deserializer.withContext(this, () -> {

            if (definition == null)
                definition = new Definition();
            definition.deserializeWith(data.getBinderOrThrow("definition"), deserializer);

            if (state == null)
                state = new State();
            state.deserealizeWith(data.getBinderOrThrow("state"), deserializer);

            if (transactional == null)
                transactional = new Transactional();
            transactional.deserializeWith(data.getBinder("transactional", null), deserializer);

            // fill references list
            if (transactional != null && transactional.references != null) {
                for(Reference ref : transactional.references) {
                    ref.setContract(this);
                    references.put(ref.name, ref);
                }
            }

            if (definition != null && definition.references != null) {
                for(Reference ref : definition.references) {
                    ref.setContract(this);
                    references.put(ref.name, ref);
                }
            }

            if (state != null && state.references != null) {
                for(Reference ref : state.references) {
                    ref.setContract(this);
                    references.put(ref.name, ref);
                }
            }
        });
    }

    @Override
    public Binder serialize(BiSerializer s) {
        Binder binder = Binder.of(
                        "api_level", apiLevel,
                        "definition", definition.serializeWith(s),
                        "state", state.serializeWith(s)
        );

        if(transactional != null)
            binder.set("transactional", transactional.serializeWith(s));

        return binder;
    }

    /**
     * Split one or more siblings from this revision. This must be a new revision (use {@link
     * #createRevision(PrivateKey...)} first. We recommend setting up signing keys before calling split, otherwise
     * caller must explicitly set signing keys on each contract.
     * <p>
     * It the new revision is already split, it can't be split again.
     * <p>
     * It is important to understand that this revision become a contract that has to be registered with Universa
     * service, which will automatically register all requested siblings in a transaction. Do not register siblings
     * themselves: registering this contract will do all the work.
     *
     * @param count number of siblings to split
     *
     * @return array of just created siblings, to modify their state only.
     */
    public Contract[] split(int count) {
        // we can split only the new revision and only once this time
        if (state.getBranchRevision() == state.revision)
            throw new IllegalArgumentException("this revision is already split");
        if (count < 1)
            throw new IllegalArgumentException("split: count should be > 0");

        // initialize context if not yet
        getContext();

        state.setBranchNumber(0);
        Contract[] results = new Contract[count];
        for (int i = 0; i < count; i++) {
            // we can't create revision as this is already a new revision, so we copy self:
            Contract c = copy();
            // keys are not COPIED by default
            c.setKeysToSignWith(new HashSet<>(getKeysToSignWith()));
            // save branch information
            c.getState().setBranchNumber(i + 1);
            // and it should refer the same parent to and set of siblings
            c.context = context;
            context.siblings.add(c);
            newItems.add(c);
            results[i] = c;
        }
        return results;
    }

    /**
     * Split this contract extracting specified value from a named field. The contract must have suitable {@link
     * com.icodici.universa.contract.permissions.SplitJoinPermission} and be signed with proper keys to pass checks.
     * <p>
     * Important. This contract must be a new revision: call {@link #createRevision(PrivateKey...)} first.
     *
     * @param fieldName      field to extract from
     * @param valueToExtract how much to extract
     *
     * @return new sibling contract with the extracted value.
     */
    public Contract splitValue(String fieldName, Decimal valueToExtract)  {
        Contract sibling = split(1)[0];
        Binder stateData = getStateData();
        Decimal value = new Decimal(stateData.getStringOrThrow(fieldName));
        stateData.set(fieldName, value.subtract(valueToExtract).toString());
        sibling.getStateData().put(fieldName, valueToExtract.toString());
        return sibling;
    }

    /**
     * If the contract is creating siblings, e.g. contracts with the same origin and parent but different branch ids,
     * this method will return them all. Note that siblings do not include this contract.
     *
     * @return list of siblings to be created together with this contract.
     */
    public Set<Contract> getSiblings() {
        return context.siblings;
    }

    /**
     * Add one or more siblings to the contract. Note that those must be sealed before calling {@link #seal()} or {@link
     * #getPackedTransaction()}. Do not reseal as it changes the id!
     *
     * @param newContracts is comma-separated contracts
     */
    public void addNewItems(Contract... newContracts) {
        for (Contract c : newContracts) {
            newItems.add(c);
        }
    }

    /**
     * Get the named field in 'dotted' notation, e.g. 'state.data.name', or 'state.origin', 'definition.issuer' and so
     * on.
     *
     * @param name of field to got value from
     * @param <T> type for value
     * @return found value
     */

    public <T> T get(String name) {
        String originalName = name;
        if (name.startsWith("definition.")) {
            name = name.substring(11);
            switch (name) {
                case "expires_at":
                    return (T) definition.expiresAt;
                case "created_at":
                    return (T) definition.createdAt;
                case "extended_type":
                    return (T) definition.extendedType;
                case "issuer":
                    return (T) getRole("issuer");
                case "owner":
                    return (T) getRole("owner");
                case "creator":
                    return (T) getRole("creator");
                case "origin":
                    return (T) getOrigin();
                default:
                    if (name.startsWith("data."))
                        return definition.data.getOrNull(name.substring(5));
                    if (name.startsWith("references."))
                        return (T) findReferenceByName(name.substring(11), "definition");
                    if (name.startsWith("permissions."))
                        return (T) findPermissisonById(name.substring(12));

            }
        } else if (name.startsWith("state.")) {
            name = name.substring(6);
            switch (name) {
                case "origin":
                    return (T) getOrigin();
                case "created_at":
                    return (T) state.createdAt;
                case "expires_at":
                    return (T) state.expiresAt;
                case "issuer":
                    return (T) getRole("issuer");
                case "owner":
                    return (T) getRole("owner");
                case "creator":
                    return (T) getRole("creator");
                case "revision":
                    return (T) ((Integer) getRevision());
                case "parent":
                    return (T) getParent();
                case "branchId":
                    return (T) state.getBranchId();
                default:
                    if (name.startsWith("data."))
                        return state.data.getOrNull(name.substring(5));
                    if (name.startsWith("references."))
                        return (T) findReferenceByName(name.substring(11), "state");
                    if (name.startsWith("roles."))
                        return (T) state.roles.get(name.substring(6));
            }
        } else if (name.startsWith("transactional.")) {
            if (transactional != null) {
                name = name.substring(14);
                switch (name) {
                    case "id":
                        return (T) transactional.id;
                    case "validUntil":
                        return (T) transactional.validUntil;
                    default:
                        if (name.startsWith("data.")) {
                            if (transactional.data == null)
                                return null;
                            return transactional.data.getOrNull(name.substring(5));
                        }
                        if (name.startsWith("references."))
                            return (T) findReferenceByName(name.substring(11), "transactional");
                }
            } else {
                return null;
            }
        } else switch (name) {
            case "id":
                return (T) getId();
            case "origin":
                return (T) getOrigin();
            case "issuer":
                return (T) getRole("issuer");
            case "owner":
                return (T) getRole("owner");
            case "creator":
                return (T) getRole("creator");
            case "tag":
                return (T) Binder.of("contractForSearchByTag", this);
            case "newItems":
            case "revokingItems":
                if (sealedBinary == null || apiLevel < 3)
                    return null;

                byte[] contractBytes = Boss.unpack(sealedBinary).getBinaryOrThrow("data");
                Binder contractData = Boss.load(contractBytes, null);
                Set<HashId> hashes = new HashSet<>();

                String key = name.replace("Items", "");
                for (Object b: contractData.getList(key, Collections.EMPTY_LIST))
                    hashes.add(HashId.withDigest(((Binder) b).getBinaryOrThrow("composite3")));

                return (T) hashes;
        }
        throw new IllegalArgumentException("bad root: " + originalName);
    }

    private Permission findPermissisonById(String id) {
        Optional<Permission> op = permissions.values().stream().filter(permission -> permission.getId().equals(id)).findAny();
        return op.orElse(null);
    }

    /**
     * Set the named field in 'dotted' notation, e.g. 'state.data.name', or 'state.origin', 'definition.issuer' and so
     * on.
     *
     * @param name of field to be set
     * @param value to be set
     */
    public void set(String name, Binder value) {
        if (name.startsWith("definition.")) {
            String subname = name.substring(11);
            switch (subname) {
                case "expires_at":
                    definition.expiresAt = value.getZonedDateTimeOrThrow("data");
                    return;
                case "created_at":
                    definition.createdAt = value.getZonedDateTimeOrThrow("data");
                    return;
                case "extended_type":
                    definition.setExtendedType(value.getStringOrThrow("data"));
                    return;
                case "issuer":
                case "owner":
                case "creator":
                    Role role = value.getOrThrow("data");
                    if (!role.getName().equals(subname))
                        throw new IllegalArgumentException("Field: " + name + " not equals role name in field value");
                    addRole(role);
                    return;
                default:
                    if (subname.startsWith("data."))
                        definition.data.set(subname.substring(5), value.getOrThrow("data"));
                    else if (subname.startsWith("references.")) {
                        Reference ref = value.getOrThrow("data");
                        if (!ref.getName().equals(subname.substring(11)))
                            throw new IllegalArgumentException("Field: " + name + " not equals reference name in field value");
                        if (ref.type != Reference.TYPE_EXISTING_DEFINITION)
                            throw new IllegalArgumentException("Field: " + name + " contains not definition-type reference in field value");

                        Reference oldRef = findReferenceByName(subname.substring(11), "definition");
                        if (oldRef != null)
                            removeReference(oldRef);

                        addReference(ref);
                    }
                    return;
            }
        } else if (name.startsWith("state.")) {
            String subname = name.substring(6);
            switch (subname) {
                case "origin":
                    if (value.getOrThrow("data").getClass().equals(HashId.class))
                        state.setOrigin(value.getOrThrow("data"));
                    else if (value.getOrThrow("data").getClass().equals(String.class))
                        state.setOrigin(new HashId(Base64u.decodeCompactString(value.getOrThrow("data"))));
                    return;
                case "created_at":
                    state.createdAt = value.getZonedDateTimeOrThrow("data");
                    return;
                case "expires_at":
                    state.expiresAt = value.getZonedDateTimeOrThrow("data");
                    return;
                case "issuer":
                case "owner":
                case "creator":
                    Role role = value.getOrThrow("data");
                    if (!role.getName().equals(subname))
                        throw new IllegalArgumentException("Field: " + name + " not equals role name in field value");
                    addRole(role);
                    return;
                case "parent":
                    if (value.getOrThrow("data").getClass().equals(HashId.class))
                        state.setParent(value.getOrThrow("data"));
                    else if (value.getOrThrow("data").getClass().equals(String.class))
                        state.setParent(new HashId(Base64u.decodeCompactString(value.getOrThrow("data"))));
                    return;
                case "revision":
                    state.setRevision(value.getIntOrThrow("data"));
                    return;
                case "branchRevision":
                    state.setBranchNumber(value.getIntOrThrow("data"));
                default:
                    if (subname.startsWith("data."))
                        state.data.set(subname.substring(5), value.getOrThrow("data"));
                    else if (subname.startsWith("references.")) {
                        Reference ref = value.getOrThrow("data");
                        if (!ref.getName().equals(subname.substring(11)))
                            throw new IllegalArgumentException("Field: " + name + " not equals reference name in field value");
                        if (ref.type != Reference.TYPE_EXISTING_STATE)
                            throw new IllegalArgumentException("Field: " + name + " contains not state-type reference in field value");

                        Reference oldRef = findReferenceByName(subname.substring(11), "state");
                        if (oldRef != null)
                            removeReference(oldRef);

                        addReference(ref);
                    }
                    return;
            }
        } else if (name.startsWith("transactional.")) {
            if (transactional != null) {
                String subname = name.substring(14);
                switch (subname) {
                    case "id":
                        transactional.setId(value.getStringOrThrow("data"));
                        return;
                    case "validUntil":
                        transactional.setValidUntil(value.getLongOrThrow("data"));
                        return;
                    default:
                        if (subname.startsWith("data."))
                            transactional.getData().set(subname.substring(5), value.getOrThrow("data"));
                        else if (subname.startsWith("references.")) {
                            Reference ref = value.getOrThrow("data");
                            if (!ref.getName().equals(subname.substring(11)))
                                throw new IllegalArgumentException("Field: " + name + " not equals reference name in field value");
                            if (ref.type != Reference.TYPE_TRANSACTIONAL)
                                throw new IllegalArgumentException("Field: " + name + " contains not transactional-type reference in field value");

                            Reference oldRef = findReferenceByName(subname.substring(11), "transactional");
                            if (oldRef != null)
                                removeReference(oldRef);

                            addReference(ref);
                        }
                        return;
                }
            }
        } else switch (name) {
            case "origin":
                if (value.getOrThrow("data").getClass().equals(HashId.class))
                    state.setOrigin(value.getOrThrow("data"));
                else if (value.getOrThrow("data").getClass().equals(String.class))
                    state.setOrigin(new HashId(Base64u.decodeCompactString(value.getOrThrow("data"))));
                return;
            case "issuer":
            case "owner":
            case "creator":
                Role role = value.getOrThrow("data");
                if (!role.getName().equals(name))
                    throw new IllegalArgumentException("Field: " + name + " not equals role name in field value");
                addRole(role);
                return;
        }
        throw new IllegalArgumentException("bad root: " + name);
    }

//    public List<Contract> extractByValidReference(List<Contract> contracts) {
//        return contracts.stream()
//                .filter(this::isValidReference)
//                .collect(Collectors.toList());
//    }
//
//    private boolean isValidReference(Contract contract) {
//        boolean resultWrap = true;
//
//        List<Reference> referencesList = this.getDefinition().getSubItems();
//
//        for (Reference references: referencesList) {
//            boolean result = true;
//
//            if (references == null) result = false;
//
//            //check roles
//            if (result) {
//                List<String> roles = references.getRoles();
//                Map<String, Role> contractRoles = contract.getRoles();
//                result = roles.stream()
//                        .anyMatch(role -> contractRoles.containsKey(role));
//            }
//
//            //check origin
//            if (result) {
//                final HashId origin = references.origin;
//                result = (origin == null || !(contract.getOrigin().equals(this.getOrigin())));
//            }
//
//
//            //check fields
//            if (result) {
//                List<String> fields = references.getFields();
//                Binder stateData = contract.getStateData();
//                result = fields.stream()
//                        .anyMatch(field -> stateData.get(field) != null);
//            }
//
//            if (!result)
//                resultWrap = false;
//        }
//
//
//        return resultWrap;
//    }

    /**
     * Construct contract from sealed binary stored in given file name
     * @param contractFileName file name to get binary from
     * @return extracted contract
     * @throws IOException
     */
    public static Contract fromSealedFile(String contractFileName) throws IOException {
        return  fromSealedBinary(Do.read(contractFileName));
    }

    /**
     * Get contract issue time
     * @return date contract issued at
     */
    public ZonedDateTime getIssuedAt() {
        return definition.createdAt;
    }

    /**
     * Get last sealed binary or create it if there is not
     *
     * @param sealAsNeed {@link #seal()} it if there is no cached binary
     *
     * @return sealed contract or null
     */
    public byte[] getLastSealedBinary(boolean sealAsNeed) {
        if (sealedBinary == null && sealAsNeed)
            seal();
        return sealedBinary;
    }

    /**
     * Pack the contract to the most modern .unicon format, same as {@link TransactionPack#pack()}. Uses bounded {@link
     * TransactionPack} instance to save together the contract, revoking and new items (if any). This is a binary format
     * using to submit for approval. Use {@link #fromPackedTransaction(byte[])} to read this format.
     *
     * @return packed binary form.
     */
    public byte[] getPackedTransaction() {
        return getTransactionPack().pack();
    }

    /**
     * Binder to hold any data client might want to keep per one transaction.
     * @return data {@link Binder} from transactional section
     */
    public Binder getTransactionalData() {
        if (transactional == null)
            createTransactionalSection();
        return transactional.getData();
    }

    /**
     * Main .unicon read routine. Load any .unicon version and construct a linked Contract with counterparts (new and
     * revoking items if present) and corresponding {@link TransactionPack} instance to pack it to store or send to
     * approval.
     * <p>
     * The supported file variants are:
     * <p>
     * - v2 legacy unicon. Is loaded with packed counterparts if any. Only for compatibility, avoid using it.
     * <p>
     * - v3 compacted unicon. Is loaded without counterparts, should be added later if need with {@link
     * #addNewItems(Contract...)} and {@link #addRevokingItems(Contract...)}. This is a good way to keep the long
     * contract chain.
     * <p>
     * - packed {@link TransactionPack}. This is a preferred way to keep current contract state.
     * <p>
     * To pack and write corresponding .unicon file use {@link #getPackedTransaction()}.
     *
     * @param packedItem some packed from of the universa contract
     * @return unpacked {@link Contract}
     * @throws IOException if the packedItem is broken
     */
    public static Contract fromPackedTransaction(@NonNull byte[] packedItem) throws IOException {
        TransactionPack tp = TransactionPack.unpack(packedItem);
        return tp.getContract();
    }

    /**
     * Set transaction pack for the contract
     * @param transactionPack transaction pack to set
     */
    public void setTransactionPack(TransactionPack transactionPack) {
        this.transactionPack = transactionPack;
    }

    /**
     * Get transaction pack of the contract
     * @return  transaction pack of the contract
     */

    public synchronized TransactionPack getTransactionPack() {
        if (transactionPack == null)
            transactionPack = new TransactionPack(this);
        return transactionPack;
    }

    /**
     * Create revocation contract. To revoke the contract it is necessary that it has "revoke" permission, and one need
     * the keys to be able to play the role assigned to it.
     * <p>
     * So, to revoke some contract:
     * <p>
     * - call {@link #createRevocation(PrivateKey...)} with key or keys that can play the role for "revoke" permission
     * <p>
     * - register it in the Universa network, see {@link com.icodici.universa.node2.network.Client#register(byte[], long)}.
     * Upon the successful registration the source contract will be revoked. Use transaction contract's {@link
     * #getPackedTransaction()} to obtain a binary to submit to the client.
     *
     * @param keys one or more keys that together can play the role assigned to the revoke permission.
     *
     * @return ready sealed contract that revokes this contract on registration
     */
    public Contract createRevocation(PrivateKey... keys) {
        return ContractsService.createRevocation(this, keys);
    }

    /**
     * Get contracts current contract revokes upon registration
     * @return contracts to be revoked
     */
    public List<Contract> getRevoking() {
        return new ArrayList<Contract>((Collection) getRevokingItems());
    }

    /**
     * Get contracts current contract creates upon registration
     * @return contracts to be created
     */
    public List<? extends Contract> getNew() {
        return new ArrayList<Contract>((Collection) getNewItems());
    }


    /**
     * Get contracts referenced by current contract. See {@link Reference}.
     * @return referenced contracts
     */
    public List<? extends Contract> getReferenced() {
        return new ArrayList<Contract>((Collection) getReferencedItems());
    }

    /**
     * @param keys that should be tested
     *
     * @return true if the set of keys is enough revoke this contract.
     * @throws Quantiser.QuantiserException if processing cost limit is got
     */
    public boolean canBeRevoked(Set<PublicKey> keys) throws Quantiser.QuantiserException {
        for (Permission perm : permissions.getList("revoke")) {
            if (perm.isAllowedForKeysQuantized(keys)){
                checkApplicablePermissionQuantized(perm);
                return true;
            }
        }
        return false;
    }

    /**
     * Create new transactional section for the contract
     * @return created transactional
     */
    public Transactional createTransactionalSection() {
        transactional = new Transactional();
        return transactional;
    }

    // processes that should be quantized

    /**
     * Verify signature, but before quantize this operation.
     * @param key that will be quantized
     * @throws Quantiser.QuantiserException if processing cost limit is got
     */
    protected void verifySignatureQuantized(PublicKey key) throws Quantiser.QuantiserException {
        // Add check signature quanta
        if(key.getBitStrength() == 2048) {
            getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_2048_SIG);
        } else {
            getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
        }
    }


    /**
     * Quantize given permission (add cost for that permission).
     * Use for permissions that will be applicated, but before checking.
     * @param permission that will be quantized
     * @throws Quantiser.QuantiserException if processing cost limit is got
     */
    public void checkApplicablePermissionQuantized(Permission permission) throws Quantiser.QuantiserException {
        // Add check an applicable permission quanta
        getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_APPLICABLE_PERM);

        // Add check a splitjoin permission	in addition to the permission check quanta
        if(permission instanceof SplitJoinPermission) {
            getQuantiser().addWorkCost(Quantiser.QuantiserProcesses.PRICE_SPLITJOIN_PERM);
        }
    }



    protected void checkSubItemQuantized(Contract contract, String prefix) throws Quantiser.QuantiserException {
        // Add checks from subItem quanta
        contract.setQuantiser(getQuantiser());
        contract.check(prefix);
    }


    /**
     * Get contract reference with given name
     * @param name name of the reference
     * @return found reference or null
     */
    public Reference findReferenceByName(String name) {
        if (getReferences() == null)
            return null;

        return getReferences().get(name);
    }

    /**
     * Get contract reference with given name in given section
     * @param name name of the reference
     * @param section section to search in
     * @return found reference or null
     */

    public Reference findReferenceByName(String name, String section) {
        if (section.equals("definition")) {
            if (definition.getReferences() == null)
                return null;

            List<Reference> listRefs = definition.getReferences();
            for (Reference ref: listRefs)
                if (ref.getName().equals(name))
                    return ref;

            return null;
        } else if (section.equals("state")) {
            if (state.getReferences() == null)
                return null;

            List<Reference> listRefs = state.getReferences();
            for (Reference ref: listRefs)
                if (ref.getName().equals(name))
                    return ref;

            return null;
        } else if (section.equals("transactional")) {
            if (transactional.getReferences() == null)
                return null;

            List<Reference> listRefs = transactional.getReferences();
            for (Reference ref: listRefs)
                if (ref.getName().equals(name))
                    return ref;

            return null;
        }
        
        return null;
    }

    public Set<String> getValidRoleReferences() {
        return validRoleReferences;
    }

    public Set<PublicKey> getEffectiveKeys() {
        /*if (effectiveKeys.isEmpty()) {
            getSealedByKeys();
            effectiveKeys = new HashMap<>(sealedByKeys);
        }*/

        return effectiveKeys.keySet();
    }

    public Set<PublicKey> getReferenceContextKeys() {
        return referenceContextKeys;
    }


    public class State {
        private int revision;
        private Binder state;
        private ZonedDateTime createdAt;
        private ZonedDateTime expiresAt;
        private HashId origin;
        private HashId parent;
        private Binder data = new Binder();
        private String branchId;
        private List<Reference> references = new ArrayList<>();
        private Map<String, Role> roles = new HashMap<>();

        private State() {
            createdAt = definition.createdAt;
            revision = 1;
        }

        public void setExpiresAt(ZonedDateTime expiresAt) {
            this.expiresAt = expiresAt.truncatedTo(ChronoUnit.SECONDS);
        }


        private State initializeWithDsl(Binder state) {
            this.state = state;
            createdAt = state.getZonedDateTime("created_at", null);
            expiresAt = state.getZonedDateTime("expires_at", null);
            revision = state.getIntOrThrow("revision");
            data = state.getOrCreateBinder("data");
            if (createdAt == null) {
                if (revision != 1)
                    throw new IllegalArgumentException("state.created_at must be set for revisions > 1");
                createdAt = definition.createdAt;
            }
            addRole("owner", state.get("owner"));
            addRole("creator", state.getOrThrow("created_by"));

            List<LinkedHashMap<String, Binder>> refList = state.getList("references", null);
            if (refList != null) {
                for (LinkedHashMap<String, Binder> refItem : refList) {
                    Binder item = new Binder(refItem);
                    Binder ref = item.getBinder("reference");
                    if (ref != null)
                        references.add(Reference.fromDslBinder(ref, getContract()));
                    else
                        throw new IllegalArgumentException("Expected reference section");
                }
            }

            return this;
        }

        public int getRevision() {
            return revision;
        }

        public ZonedDateTime getCreatedAt() {
            return createdAt;
        }

        public Binder serializeWith(BiSerializer serializer) {

            Binder of = Binder.of(
                    "created_at", createdAt,
                    "revision", revision,
                    "owner", getRole("owner"),
                    "created_by", getRole("creator"),
                    "branch_id", branchId,
                    "origin", origin,
                    "parent", parent,
                    "data", data,
                    "roles", serializer.serialize(new HashMap<>(roles))
            );

            if (expiresAt != null)
                of.set("expires_at", expiresAt);

            if (references != null)
                of.set("references", references);

            return serializer.serialize(
                    of
            );
        }

        public Binder getData() {
            if( data == null )
                data = new Binder();
            return data;
        }

        public void deserealizeWith(Binder data, BiDeserializer d) {
            createdAt = data.getZonedDateTimeOrThrow("created_at");
            expiresAt = data.getZonedDateTime("expires_at", null);

            revision = data.getIntOrThrow("revision");

            this.references = d.deserialize(data.getList("references", null));

            if (revision <= 0)
                throw new IllegalArgumentException("illegal revision number: " + revision);
            Role r = addRole(d.deserialize(data.getBinderOrThrow("owner")));
            if (!r.getName().equals("owner"))
                throw new IllegalArgumentException("bad owner role name");
            r = addRole(d.deserialize(data.getBinderOrThrow("created_by")));
            if (!r.getName().equals("creator"))
                throw new IllegalArgumentException("bad creator role name");
            try {
                this.data = d.deserialize(data.getBinder("data", Binder.EMPTY));
            } catch (Exception e) {
                //backward compatibility
                this.data = data.getBinder("data", Binder.EMPTY);
            }
            branchId = data.getString("branch_id", null);
            parent = d.deserialize(data.get("parent"));
            origin = d.deserialize(data.get("origin"));
            Object o = data.get("roles");
            if(o == null) {
                this.roles = new HashMap<>();
            } else if(o instanceof Map) {
                this.roles = d.deserialize(o);
            } else if(o instanceof List) {
                List<Role> roleObjects = d.deserialize(o);
                roleObjects.forEach(ro->this.roles.put(ro.getName(),ro));
            }
        }

        private Integer branchRevision = null;

        /**
         * Revision at which this branch was split
         *
         * @return branch revision as int
         */
        public Integer getBranchRevision() {
            if (branchRevision == null) {
                if (branchId == null)
                    branchRevision = 0;
                else
                    // we usually don't need sibling number here
                    branchRevision = Integer.valueOf(branchId.split(":")[0]);
            }
            return branchRevision;
        }

        public String getBranchId() {
            return branchId;
        }

        public void setBranchNumber(int number) {
            branchId = revision + ":" + number;
            branchRevision = number;
        }

        public void addReference(Reference reference) {
            if(references == null) {
                references = new ArrayList<>();
            }

            references.add(reference);
        }

        public void removeReference(Reference reference) {
            if(references == null) {
                return;
            }
            references.remove(reference);
        }

        public List<Reference> getReferences() {
            return this.references;
        }

        public void setParent(HashId parent) {
            this.parent = parent;
        }

        public void setOrigin(HashId origin) {
            this.origin = origin;
        }

        public void setRevision(int revision) {
            this.revision = revision;
        }
        /**
         * Saves client's javascript in contract's state. It can be executed with {@link Contract#execJS(JSApiExecOptions, byte[], String...)}
         */
        public void setJS(byte[] jsFileContent, String jsFileName, JSApiScriptParameters scriptParameters, boolean putContentIntoContract) {
            String fileNameKey = JSApiHelpers.fileName2fileKey(jsFileName);
            Binder scriptBinder = JSApiHelpers.createScriptBinder(jsFileContent, jsFileName, scriptParameters, putContentIntoContract);
            Binder scripts = getData().getBinder(JSAPI_SCRIPT_FIELD, new Binder());
            scripts.set(fileNameKey, scriptBinder);
            getData().put(JSAPI_SCRIPT_FIELD, scripts);
        }

        public void setJS(byte[] jsFileContent, String jsFileName, JSApiScriptParameters scriptParameters) {
            setJS(jsFileContent, jsFileName, scriptParameters, false);
        }

    }

    private Multimap<String, Permission> permissions = new Multimap<>();

    public Contract getContract() {
        return this;
    }

    public class Definition {

        private ZonedDateTime createdAt;

        public void setExpiresAt(ZonedDateTime expiresAt) {
            this.expiresAt = expiresAt;
        }

        public void setData(Binder data) {
            this.data = data;
        }

        private ZonedDateTime expiresAt;
        private Binder definition;
        private Binder data = new Binder();
        private List<Reference> references = new ArrayList<>();

        private String extendedType;
        public void setExtendedType(String extendedType) {
            this.extendedType = extendedType;
            if(definition != null)
                definition.set("extended_type", extendedType);
        }
        public String getExtendedType() {
            return extendedType;
        }

        private Definition() {
            createdAt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        }

        private Definition initializeWithDsl(Binder definition) {
            this.definition = definition;
            Role issuer = addRole("issuer", definition.getOrThrow("issuer"));
            createdAt = definition.getZonedDateTimeOrThrow("created_at");
            Object t = definition.getOrDefault("expires_at", null);
            if (t != null)
                expiresAt = decodeDslTime(t);
            addRole(issuer);
            data = definition.getBinder("data");

            extendedType = definition.getString("extended_type", null);

            List<LinkedHashMap<String, Binder>> refList = definition.getList("references", null);
            if (refList != null) {
                for (LinkedHashMap<String, Binder> refItem : refList) {
                    Binder item = new Binder(refItem);
                    Binder ref = item.getBinder("reference");
                    if (ref != null)
                        references.add(Reference.fromDslBinder(ref, getContract()));
                    else
                        throw new IllegalArgumentException("Expected reference section");
                }
            }

            return this;
        }

        public void addReference(Reference reference) {
            if(references == null) {
                references = new ArrayList<>();
            }

            references.add(reference);
        }

        public void removeReference(Reference reference) {
            if(references == null) {
                return;
            }

            references.remove(reference);
        }

        public List<Reference> getReferences() {
            return this.references;
        }

        /**
         * Saves client's javascript in contract's definition. It can be executed with {@link Contract#execJS(JSApiExecOptions, byte[], String...)}
         * @param putContentIntoContract pass here true to save js body in contract, in this case it's not necessary to store attached
         *                               js-files separately, needs only {@link HashId} of its.
         */
        public void setJS(byte[] jsFileContent, String jsFileName, JSApiScriptParameters scriptParameters, boolean putContentIntoContract) {
            String fileNameKey = JSApiHelpers.fileName2fileKey(jsFileName);
            Binder scriptBinder = JSApiHelpers.createScriptBinder(jsFileContent, jsFileName, scriptParameters, putContentIntoContract);
            Binder scripts = getData().getBinder(JSAPI_SCRIPT_FIELD, new Binder());
            scripts.set(fileNameKey, scriptBinder);
            getData().put(JSAPI_SCRIPT_FIELD, scripts);
        }

        public void setJS(byte[] jsFileContent, String jsFileName, JSApiScriptParameters scriptParameters) {
            setJS(jsFileContent, jsFileName, scriptParameters, false);
        }

        /**
         * Collect all permissions and create links to roles or new roles as appropriate
         */
        private void scanDslPermissions() {
            definition.getBinderOrThrow("permissions").forEach((name, params) -> {
                // this complex logic is needed to process both yaml-imported structures
                // and regular serialized data in the same place
                if (params instanceof Object[])
                    for (Object x : (Object[]) params)
                        loadDslPermission(name, x);
                else if (params instanceof List)
                    for (Object x : (List) params)
                        loadDslPermission(name, x);
                else if (params instanceof Permission)
                    addPermission((Permission) params);
                else
                    loadDslPermission(name, params);
            });
        }

        private void loadDslPermission(String name, Object params) {
            String roleName = null;
            Role role = null;
            Binder binderParams = null;
            if (params instanceof CharSequence)
                // yaml style: permission: role
                roleName = params.toString();
            else {
                // extended yaml style or serialized object
                binderParams = Binder.from(params);
                Object x = binderParams.getOrThrow("role");
                if (x instanceof Role) {
                    // serialized, role object
                    role = addRole((Role) x);
                }
                else if (x instanceof Map) {
                    // if Map object - create role from Map
                    role = createRole("@" + name, (Map) x,false);
                }
                else {
                    // yaml, extended form: permission: { role: name, ... }
                    roleName = x.toString();
                }
            }
            if (role == null && roleName != null) {
                // we need to create alias to existing role
                role = createRole("@" + name, roleName,false);
            }
            if (role == null)
                throw new IllegalArgumentException("permission " + name + " refers to missing role: " + roleName);
            // now we have ready role and probably parameter for custom rights creation
            addPermission(Permission.forName(name, role, params instanceof String ? null : binderParams));
        }

        public Binder getData() {
            if (data == null)
                data = new Binder();
            return data;
        }

        public Binder serializeWith(BiSerializer serializer) {
            List<Permission> pp = permissions.values();
            Binder pb = new Binder();
            int lastId = 0;

            // serialize permissions with a valid id
            permissions.values().forEach(perm -> {
                String pid = perm.getId();
                if (pid == null)
                    throw new IllegalStateException("permission without id: " + perm);
                if (pb.containsKey(pid))
                    throw new IllegalStateException("permission: duplicate permission id found: " + perm);
                pb.put(pid, perm);
            });

            Collections.sort(pp);
            Binder of = Binder.of(
                    "issuer", getIssuer(),
                    "created_at", createdAt,
                    "data", data,
                    "permissions", pb
            );

            if (expiresAt != null)
                of.set("expires_at", expiresAt);

            if (references != null)
                of.set("references", references);

            if (extendedType != null)
                of.set("extended_type", extendedType);

            return serializer.serialize(of);
        }

        public void deserializeWith(Binder data, BiDeserializer d) {
            addRole(d.deserialize(data.getBinderOrThrow("issuer")));
            createdAt = data.getZonedDateTimeOrThrow("created_at");
            expiresAt = data.getZonedDateTime("expires_at", null);
            extendedType = data.getString("extended_type", null);
            this.data = d.deserialize(data.getBinder("data", Binder.EMPTY));
            references = d.deserialize(data.getList("references", null));
            Map<String, Permission> perms = d.deserialize(data.getOrThrow("permissions"));
            perms.forEach((id, perm) -> {
                perm.setId(id);
                addPermission(perm);
            });
        }

        public ZonedDateTime getExpiresAt() {
            return expiresAt;
        }

        public ZonedDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(ZonedDateTime createAt) {
            this.createdAt = createAt;
        }
    }

    /**
     * This section of a contract need for complex contracts, that consist of some contracts and need to be register all or no one.
     * F.e. contract that has contracts in revoking or new items and new item shouldn't be registered separately.
     * To do it, add transactional section with references to another contracts or their transactional sections.
     * And that contracts will can be registered only together.
     *
     * Transactional lives only one revision, so if you created new revision from contract, section is became null.
     *
     * Section does not check for allowed modification.
     */
    public class Transactional {

        private String id;
        private List<Reference> references;
        private Long validUntil;
        private Binder data;

        private Transactional() {

        }

        public Binder serializeWith(BiSerializer serializer) {

            Binder b = Binder.of(
                    "id", id
            );

            if (references != null)
                b.set("references", serializer.serialize(references));

            if (validUntil != null)
                b.set("valid_until", validUntil);

            if (data != null)
                b.set("data", serializer.serialize(data));

            return serializer.serialize(b);
        }

        public void deserializeWith(Binder data, BiDeserializer d) {
            if(data != null) {
                id = data.getString("id", null);
                List refs = data.getList("references", null);
                if(refs != null) {
                    references = d.deserializeCollection(refs);
                }
                try {
                    validUntil = data.getLongOrThrow("valid_until");
                } catch (IllegalArgumentException e) {
                    validUntil = null;
                }
                this.data = d.deserialize(data.getBinder("data", Binder.EMPTY));
            }
        }

        public void addReference(Reference reference) {
            if(references == null) {
                references = new ArrayList<>();
            }

            references.add(reference);
        }

        public void removeReference(Reference reference) {
            if(references == null) {
                return;
            }

            references.remove(reference);
        }

        public List<Reference> getReferences() {
            return references;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Long getValidUntil() {
            return validUntil;
        }

        public void setValidUntil(Long val) {
            validUntil = val;
        }

        public Binder getData() {
            if (data == null)
                data = new Binder();
            return data;
        }
    }

    public void traceErrors() {
        try {
            for (ErrorRecord er : errors) {
                System.out.println("Error: " + er);
            }
        } catch (ConcurrentModificationException e) {
            e.printStackTrace();
        }
    }

    public String getErrorsString() {
        return errors.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return copy();
    }

    /**
     * Make a valid deep copy of a contract
     *
     * @return instance of {@link Contract}
     */
    public synchronized Contract copy() {
        return Boss.load(Boss.dump(this));
    }

    protected Context getContext() {
        if (context == null) {
            context = new Context(getRevokingItem(getParent()));
            context.siblings.add(this);
            newItems.forEach(i -> {
                if (i.getParent() != null && i.getParent().equals(getParent()))
                    context.siblings.add(i);
            });

        }
        return context;
    }

    @Override
    public boolean isU(Set<KeyAddress> issuerKeys, String issuerName) {
        Role issuer = getIssuer();
        if(!(issuer instanceof SimpleRole))
            return false;


        Set<KeyAddress> thisIssuerAddresses = new HashSet<>(((SimpleRole)issuer).getSimpleKeyAddresses());
        for (PublicKey publicKey : ((SimpleRole)issuer).getSimpleKeys())
            thisIssuerAddresses.add(publicKey.getShortAddress());
        if (Collections.disjoint(issuerKeys, thisIssuerAddresses))
            return false;
        if ( !issuerName.equals(getDefinition().getData().get("issuerName")))
            return false;
        return true;
    }

    @Override
    public boolean shouldBeU() {
        return shouldBeU;
    }
    public void setShouldBeU(boolean shouldBeU) {
        this.shouldBeU = shouldBeU;
    }

    public void setLimitedForTestnet(boolean limitedForTestnet) {
        this.limitedForTestnet = limitedForTestnet;
    }

    public boolean isLimitedForTestnet() {
        return limitedForTestnet;
    }

    /**
     * Use after check(). Shows if contract is fit testnet criteria.
     * @return true if can registered in the testnet or false if no.
     */
    public boolean isSuitableForTestnet() {
        return isSuitableForTestnet;
    }

    @Override
    public boolean isInWhiteList(List<PublicKey> whiteList) {
        return getSealedByKeys().stream().anyMatch(k -> whiteList.contains(k));
    }


    /**
     * Transaction context. Holds temporary information about a context transaction relevant to create sibling, e.g.
     * contract splitting. Allow new items being created to get the base contract (that is creating) and get the full
     * list of siblings.
     */
    protected class Context {
        private final Set<Contract> siblings = new HashSet<>();
        private final Contract base;

        public Context(@NonNull Contract base) {
            this.base = base;
        }
    }

    final public class ContractDev {

        private Contract c;

        public ContractDev(Contract c) throws Exception {
            this.c = c;
        }

        public void setOrigin(HashId origin) {
            this.c.getState().origin = origin;
        }

        public void setParent(HashId parent) {
            this.c.getState().parent = parent;
        }

        public Contract getContract() {
            return this.c;
        }
    }

    static private Pattern relativeTimePattern = Pattern.compile(
            "(\\d+) (hour|min|day)\\w*$",
            Pattern.CASE_INSENSITIVE);

    static public ZonedDateTime decodeDslTime(Object t) {
        if (t instanceof ZonedDateTime)
            return (ZonedDateTime) t;
        if (t instanceof CharSequence) {
            if (t.equals("now()"))
                return ZonedDateTime.now();
            Matcher m = relativeTimePattern.matcher((CharSequence) t);
            System.out.println("MATCH: " + m);
            if (m.find()) {
                ZonedDateTime now = ZonedDateTime.now();
                int amount = Integer.valueOf(m.group(1));
                String unit = m.group(2);
                switch (unit) {
                    case "min":
                        return now.plusMinutes(amount);
                    case "hour":
                        return now.plusHours(amount);
                    case "day":
                        return now.plusDays(amount);
                    default:
                        throw new IllegalArgumentException("unknown time unit: " + unit);

                }
            }
        }
        throw new IllegalArgumentException("can't convert to datetime: "+t);
    }

    /**
     * Executes javascript, that previously should be saved in contract's definition
     * with {@link Definition#setJS(byte[], String, JSApiScriptParameters)} or {@link State#setJS(byte[], String, JSApiScriptParameters)}.
     * Provides instance of {@link JSApi} to this script, as 'jsApi' global var.
     * @param params list of strings, will be passed to javascript
     * @return Object, got it from 'result' global var of javascript.
     * @throws ScriptException if javascript throws some errors
     * @throws IllegalArgumentException if javascript is not defined in contract's definition
     */
    public Object execJS(JSApiExecOptions execOptions, byte[] jsFileContent, String... params) throws Exception {
        JSApiEnvironment env = JSApiEnvironment.execJS(
                getDefinition().getData().getBinder(JSAPI_SCRIPT_FIELD, null),
                getState().getData().getBinder(JSAPI_SCRIPT_FIELD, null),
                execOptions,
                jsFileContent,
                this,
                params
        );
        env.callEvent("main", true);
        return env.getResult();
    }

    public Object execJS(byte[] jsFileContent, String... params) throws Exception {
        return execJS(new JSApiExecOptions(), jsFileContent, params);
    }

    /**
     * Executes javascript, like {@link Contract#execJS(JSApiExecOptions, byte[], String...)},
     * but searches script body in contract. It should be saved previously with {@link Definition#setJS(byte[], String, JSApiScriptParameters, boolean)}
     * or {@link State#setJS(byte[], String, JSApiScriptParameters, boolean)} with putContentIntoContract == true.
     * @param scriptHash is {@link HashId} from js-file content.
     */
    public Object execJSByScriptHash(JSApiExecOptions execOptions, HashId scriptHash, String... params) throws Exception {
        JSApiEnvironment env = JSApiEnvironment.execJSByScriptHash(
                getDefinition().getData().getBinder(JSAPI_SCRIPT_FIELD, null),
                getState().getData().getBinder(JSAPI_SCRIPT_FIELD, null),
                execOptions,
                scriptHash,
                this,
                params
        );
        return env.callEvent("main", true);
    }

    public Object execJSByScriptHash(HashId scriptHash, String... params) throws Exception {
        return execJSByScriptHash(new JSApiExecOptions(), scriptHash, params);
    }

    /**
     * Executes attached javascript if only one js is attached. Also, it should be attached with putContentIntoContract == true.
     */
    public Object execJSByName(JSApiExecOptions execOptions, String jsFileName, String... params) throws Exception {
        JSApiEnvironment env = JSApiEnvironment.execJSByName(
                getDefinition().getData().getBinder(JSAPI_SCRIPT_FIELD, null),
                getState().getData().getBinder(JSAPI_SCRIPT_FIELD, null),
                execOptions,
                jsFileName,
                this,
                params
        );
        return env.callEvent("main", true);
    }

    public Object execJSByName(String jsFileName, String... params) throws Exception {
        return execJSByName(new JSApiExecOptions(), jsFileName, params);
    }

    public List<String> extractJSNames() {
        List<String> res = new ArrayList<>();
        Binder scriptBinder = getDefinition().getData().getBinder(JSAPI_SCRIPT_FIELD, null);
        if (scriptBinder == null)
            scriptBinder = getState().getData().getBinder(JSAPI_SCRIPT_FIELD, null);
        if (scriptBinder != null)
            res.addAll(JSApiHelpers.getFileNamesFromScriptBinder(scriptBinder));
        return res;
    }

    static {
        Config.forceInit(ItemResult.class);
        Config.forceInit(HashId.class);
        Config.forceInit(Contract.class);
        Config.forceInit(Permission.class);
        Config.forceInit(Contract.class);
        Config.forceInit(ChangeNumberPermission.class);
        Config.forceInit(ChangeOwnerPermission.class);
        Config.forceInit(ChangeRolePermission.class);
        Config.forceInit(SplitJoinPermission.class);
        Config.forceInit(PublicKey.class);
        Config.forceInit(PrivateKey.class);
        Config.forceInit(KeyRecord.class);
        Config.forceInit(KeyAddress.class);
        Config.forceInit(AnonymousId.class);

        DefaultBiMapper.registerClass(Contract.class);
        DefaultBiMapper.registerClass(ChangeNumberPermission.class);
        DefaultBiMapper.registerClass(ChangeOwnerPermission.class);
        DefaultBiMapper.registerClass(ChangeRolePermission.class);
        DefaultBiMapper.registerClass(ModifyDataPermission.class);
        DefaultBiMapper.registerClass(RevokePermission.class);
        DefaultBiMapper.registerClass(SplitJoinPermission.class);
        // roles
        DefaultBiMapper.registerClass(ListRole.class);
        DefaultBiMapper.registerClass(Role.class);
        DefaultBiMapper.registerClass(RoleLink.class);
        DefaultBiMapper.registerClass(SimpleRole.class);
        DefaultBiMapper.registerClass(QuorumVoteRole.class);
        // other
        DefaultBiMapper.registerClass(KeyRecord.class);
        DefaultBiMapper.registerAdapter(PublicKey.class, PUBLIC_KEY_BI_ADAPTER);
        DefaultBiMapper.registerClass(Reference.class);

        DefaultBiMapper.registerClass(Permission.class);
        DefaultBiMapper.registerClass(PaidOperation.class);
    }

    public static Contract fromSealedBinary(byte[] sealedBinary) throws IOException {
        return fromSealedBinary(sealedBinary,new TransactionPack());
    }

    public static Contract fromSealedBinary(byte[] sealedBinary, TransactionPack tp) throws IOException {
        Binder data = Boss.unpack(sealedBinary);

        if (!data.getStringOrThrow("type").equals("unicapsule"))
            throw new UnicapsuleExpectedException("wrong object type, unicapsule required");


        byte[] contractBytes = data.getBinaryOrThrow("data");

        // This must be explained. By default, Boss.load will apply contract transformation in place
        // as it is registered BiSerializable type, and we want to avoid it. Therefore, we decode boss
        // data without BiSerializer and then do it by hand calling deserialize:
        Binder payload = Boss.load(contractBytes, null);

        // contract can be extended type - we need know about it before
        String extendedType = payload.getBinder("contract").getBinder("definition").getString("extended_type", null);

        NSmartContract.SmartContractType scType = null;
        if(extendedType != null) {
            try {
                scType = NSmartContract.SmartContractType.valueOf(extendedType);
            } catch (IllegalArgumentException e) {
            }
        }
        Contract result;
        // and if extended type of contract is allowed - create extended contrac, otherwise create simple contract
        if(scType != null) {
            switch(scType) {
                case N_SMART_CONTRACT:
                    result = new NSmartContract(data, tp);
                    break;

                case SLOT1:
                    result = new SlotContract(data, tp);
                    break;
                case UNS1:
                    result = new UnsContract(data, tp);
                    break;
                case FOLLOWER1:
                    result = new FollowerContract(data, tp);
                    break;
                default:
                    //unknwon extended type. create simple contract
                    //TODO: should we throw?
                    result =  new Contract(data, tp);
            }
        } else {
            result =  new Contract(data, tp);
        }
        result.sealedBinary = sealedBinary;
        return result;
    }

    /**
     * Attach current contract to an instance of Universa network.
     *
     * This will ensure that all the parameters used by {@link NSmartContract} for local calculations
     * match the ones connected network is using.
     *
     * @param client connection to an instance of Universa network.
     * @throws ClientError
     */
    public void attachToNetwork(Client client) throws ClientError {
        serviceContracts = client.getServiceContracts();
    }

}
