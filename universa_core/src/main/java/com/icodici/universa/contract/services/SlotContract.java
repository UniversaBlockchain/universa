package com.icodici.universa.contract.services;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SlotContract extends NSmartContract {

    private byte[] packedTrackingContract;
    private Contract trackingContract;
    private int keepRevisions = 1;


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
    public SlotContract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        super(sealed, pack);
        getDefinition().setExtendedType(SmartContractType.SLOT1.name());

        // add modify_data permission

        RoleLink ownerLink = new RoleLink("owner_link", "owner");
        registerRole(ownerLink);
        HashMap<String,Object> fieldsMap = new HashMap<>();
        fieldsMap.put("action", null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        addPermission(modifyDataPermission);
    }

    public SlotContract() {
        super();
        getDefinition().setExtendedType(SmartContractType.SLOT1.name());

        // add modify_data permission

        RoleLink ownerLink = new RoleLink("owner_link", "owner");
        registerRole(ownerLink);
        HashMap<String,Object> fieldsMap = new HashMap<>();
        fieldsMap.put("action", null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        addPermission(modifyDataPermission);
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
    public SlotContract(PrivateKey key) {
        super(key);
        getDefinition().setExtendedType(SmartContractType.SLOT1.name());

        // add modify_data permission

        RoleLink ownerLink = new RoleLink("owner_link", "owner");
        registerRole(ownerLink);
        HashMap<String,Object> fieldsMap = new HashMap<>();
        fieldsMap.put("action", null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        addPermission(modifyDataPermission);
    }

    protected SlotContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        int numRevisions = root.getBinder("state").getBinder("data").getInt("keep_revisions", -1);
        if(numRevisions > 0)
            keepRevisions = numRevisions;
        return this;
    }

    public static SlotContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new SlotContract().initializeWithDsl(binder);
        }
    }

    public byte[] getPackedContract() {
        return packedTrackingContract;
    }

    @Override
    public Contract getContract() {
        return trackingContract;
    }

    public void setPackedContract(byte[] packed) throws IOException {
        trackingContract = TransactionPack.unpack(packed).getContract();
        packedTrackingContract = packed;
        getStateData().set("tracking_contract", getPackedContract());
    }

    public void setContract(Contract c) {
        packedTrackingContract = c.getPackedTransaction();
        trackingContract = c;
        getStateData().set("tracking_contract", getPackedContract());
    }

    public int getKeepRevisions() {
        return keepRevisions;
    }

    @Override
    public void onContractStorageSubscriptionEvent(ContractStorageSubscription.Event event) {
        MutableEnvironment me;
        if(event instanceof ContractStorageSubscription.ApprovedEvent) {
            Contract newStoredItem = ((ContractStorageSubscription.ApprovedEvent)event).getNewRevision();
            me = new NMutableEnvironment(this);
            ContractStorageSubscription css = me.createStorageSubscription(newStoredItem.getId(), getExpiresAt());
            css.receiveEvents(true);
//            ledger.addEnvironmentToStorage(newStoredItem.getId(), Boss.pack(me), getId());
            // todo: update this to 'environments'
            // todo: save css to 'subscriptions'
            // todo: save link me -> css to 'environment_subscriptions'
            // todo: save newStoredItem to 'storages'
        } else if(event instanceof ContractStorageSubscription.RevokedEvent) {

        }

        event.getSubscription().destroy();
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        int numRevisions = data.getBinder("state").getBinder("data").getInt("keep_revisions", -1);
        if(numRevisions > 0)
            keepRevisions = numRevisions;

        try {
            setPackedContract(data.getBinder("state").getBinder("data").getBinary("tracking_contract"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean beforeCreate(ImmutableEnvironment c) {
        return additionallySlotCheck(c);
    }

    @Override
    public boolean beforeUpdate(ImmutableEnvironment c) {
        return additionallySlotCheck(c);
    }

    @Override
    public boolean beforeRevoke(ImmutableEnvironment c) {
        return additionallySlotCheck(c);
    }

    private boolean additionallySlotCheck(ImmutableEnvironment c) {

//        boolean checkResult = false;
//
//        checkResult = c != null && c.getContract() != null;
//        if(!checkResult) {
//            addError(FAILED_CHECK, "Environment should be not null and should have contract");
//            return checkResult;
//        }
//
//        checkResult = getExtendedType().equals(SmartContractType.SLOT1.name());
//        if(!checkResult) {
//            addError(FAILED_CHECK, "definition.extended_type", "illegal value, should be " + SmartContractType.SLOT1.name());
//            return checkResult;
//        }
//
//        checkResult = c.getContract().getOwner().isAllowedForKeys(getCreator().getKeys());
//        if(!checkResult) {
//            addError(FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
//            return checkResult;
//        }
//
//        return checkResult;

        return true;
    }

    static {
        Config.forceInit(SlotContract.class);
        DefaultBiMapper.registerClass(SlotContract.class);
    }
}
