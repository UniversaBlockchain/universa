package com.icodici.universa.contract.helpers;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class Compound {
    public static final String TYPE = "universa_compound";
    public static final int VERSION = 1;

    private Contract compoundContract;

    /**
     * Create Compound from compound contract
     * @param compoundContract unpacked compound contract
     */

    public Compound(Contract compoundContract) {
        this.compoundContract = compoundContract;
        String type = compoundContract.getDefinition().getData().getString("type","");
        if(!type.equals(TYPE)) {
            throw new IllegalArgumentException("Invalid 'definition.data.type':'"+type+"', expected:'"+TYPE+"'");
        }


        int version = compoundContract.getDefinition().getData().getInt("version",999999);
        if(version > VERSION) {
            throw new IllegalArgumentException("'definition.data.version':'"+version+"' is not supported. Maximum supported version is " + VERSION);
        }
    }

    /**
     * Create Compound from packed transaction of compound contract
     * @param packedTransaction
     * @throws IOException
     */
    public Compound(byte[] packedTransaction) throws IOException {
        this(Contract.fromPackedTransaction(packedTransaction));
    }

    /**
     * Create a compound contract, nested contracts can be added to
     *
     * There are two possible usages of compound contract technique.
     * First is registering multiple contracts in single transaction, saving time and reducing U cost.
     * Second is adding signatures to compound contract that will affect nested contracts without changing their binaries/IDs
     */

    public Compound() {
        compoundContract = new Contract();
        compoundContract.setExpiresAt(ZonedDateTime.now().plusDays(14));

        //in order ot create roles without keys we create dummy reference and add it as required for the roles
        Reference dummyReference = new Reference(compoundContract);
        dummyReference.name = "dummy";
        dummyReference.setConditions(Binder.of("any_of", Do.listOf("this.state.parent undefined")));
        compoundContract.addReference(dummyReference);

        SimpleRole issuer = new SimpleRole("issuer",compoundContract);
        issuer.addRequiredReference("dummy", Role.RequiredMode.ALL_OF);
        compoundContract.addRole(issuer);


        compoundContract.addRole(new RoleLink("creator",compoundContract,"issuer"));
        compoundContract.addRole(new RoleLink("owner",compoundContract,"issuer"));

        compoundContract.getDefinition().getData().put("type",TYPE);
        compoundContract.getDefinition().getData().put("version",VERSION);

        compoundContract.getDefinition().getData().put("contracts",new Binder());

        compoundContract.seal();
    }

    /**
     * Returns compound that holds a contract with given ID (if exists)
     * @param contractInPack id of a contract held by compound
     * @param transactionPack transaction pack to look into
     * @return compound if exists of {@code null} otherwise
     */

    public static Compound getHoldingCompound(HashId contractInPack, TransactionPack transactionPack) {
        Contract compoundContract = transactionPack.findContract(c -> c.getNewItems().stream().filter(ni->ni.getId().equals(contractInPack)).findAny().isPresent());
        if(compoundContract != null && compoundContract.getDefinition().getData().getString("type","").equals(TYPE)) {
            return  new Compound(compoundContract);
        } else {
            return null;
        }
    }

    /**
     * Add contract to Compound. Contract and its data will later be accessible by its tag.
     *
     * Note: contract returned by {@link #getContract(String)} has reconstructed transaction pack
     * with referenced items include
     *
     * @param tag string associated with contract being added
     * @param contractToPutInto contract being added
     * @param dataToAssociateWith binder associated with contract being added
     */

    public void addContract(String tag, Contract contractToPutInto, Binder dataToAssociateWith) {
        compoundContract.addNewItems(contractToPutInto);


        Binder tagBinder = new Binder();

        Collection<Contract> referencedItems = contractToPutInto.getTransactionPack().getReferencedItems().values();
        Map<String, Contract> tpTags = contractToPutInto.getTransactionPack().getTags();

        tagBinder.put("id",contractToPutInto.getId().toBase64String());
        tagBinder.put("data",dataToAssociateWith);
        tagBinder.put("refs", referencedItems.stream().map(ri->ri.getId().toBase64String()).collect(Collectors.toList()));
        Binder tpTagsBinder = new Binder();
        tpTags.forEach((k, v) -> tpTagsBinder.put(k, v.getId().toBase64String()));
        tagBinder.put("tags", tpTagsBinder);

        compoundContract.getDefinition().getData().getBinder("contracts").put(tag,tagBinder);

        compoundContract.seal();

        referencedItems.forEach(ri->compoundContract.getTransactionPack().addReferencedItem(ri));

    }


    /**
     * Get contract from Compound by id
     *
     * @param contractId id of a contract in compound
     * @return contract found
     */

    public Contract getContract(HashId contractId) {
        Binder tagsBinder = compoundContract.getDefinition().getData().getBinder("contracts");
        Set<String> tags = tagsBinder.keySet();
        for(String tag : tags) {
            HashId id = HashId.withDigest(tagsBinder.getBinder(tag).getString("id"));
            if(id.equals(contractId)) {
                return getContract(tag);
            }
        }
        return null;
    }

    /**
     * Get contract from Compound by tag
     *
     * @param tag string to find contract by
     * @return contract found
     */

    public Contract getContract(String tag) {

        try {
            //get binder for tag specified
            Binder tagBinder = compoundContract.getDefinition().getData().getBinder("contracts").getBinder(tag,null);
            if(tagBinder == null)
                return null;


            //get contract by id and create transaction pack for it
            HashId id = HashId.withDigest(tagBinder.getString("id"));
            Contract contract = compoundContract.getTransactionPack().getSubItem(id);
            TransactionPack transactionPack = new TransactionPack(contract);
            contract.setTransactionPack(transactionPack);

            //fill tp referenced items from refs
            List<String> referencedItemsIds = tagBinder.getList("refs", new ArrayList<>());
            referencedItemsIds.forEach(riId ->transactionPack.addReferencedItem(
                    compoundContract.getTransactionPack().getReferencedItems().get(HashId.withDigest(riId))));

            //fill tp tags from "tags"
            Binder tpTagsBinder = tagBinder.getBinder("tags",new Binder());
            tpTagsBinder.forEach((k,v)->transactionPack.addTag(k,HashId.withDigest((String) v)));

            return contract;

        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Get contract associated data from Compound by tag
     *
     * @param tag string to find data by
     * @return contract associated data
     */

    public Binder getData(String tag) {
        Binder tagBinder = compoundContract.getDefinition().getData().getBinder("contracts").getBinder(tag);
        return tagBinder.getBinder("data");
    }

    /**
     * Get tags from Compound
     *
     * @return tags
     */

    public Set<String> getTags() {
        return compoundContract.getDefinition().getData().getBinder("contracts").keySet();
    }

    /**
     * Get contract that holds tags,contracts and data associated. Must be registered in order to register Compound entries
     * @return contract
     */

    public Contract getCompoundContract() {
        return compoundContract;
    }
}
