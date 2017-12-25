/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.util.*;

public class ContractsService extends Contract {

//    public ContractsService(byte[] sealed) throws IOException {
//        super(sealed);
//    }
//
//    public ContractsService(byte[] sealed, TransactionPack pack) throws IOException {
//        super(sealed, pack);
//    }
//
//    public ContractsService() {
//        super();
//        Definition cd = getDefinition();
//        // by default, transactions expire in 30 days
//        cd.setExpiresAt(getCreatedAt().plusDays(30));
//    }
//
//    /**
//     * The only call needed to setup roles and rights and signatures for {@link ContractsService}.
//     * <p>
//     * Transaction contract is immutable, and is issued, onwed and created by the same role, so we create it all in one
//     * place, with at least one privateKey. Do not change any roles directly.
//     *
//     * @param issuers
//     */
//    public void setIssuer(PrivateKey... issuers) {
//        SimpleRole issuerRole = new SimpleRole("issuer");
//        for (PrivateKey k : issuers) {
//            KeyRecord kr = new KeyRecord(k.getPublicKey());
//            issuerRole.addKeyRecord(kr);
//            addSignerKey(k);
//        }
//        registerRole(issuerRole);
//        createRole("owner", issuerRole);
//        createRole("creator", issuerRole);
//    }
//
//    public void setIssuer(PublicKey... swapperKeys) {
//        ListRole issuerRole = new ListRole("issuer");
//        for (int i = 0; i < swapperKeys.length; i++) {
//            SimpleRole swapperRole = new SimpleRole("swapper" + (i+1));
//
//            swapperRole.addKeyRecord(new KeyRecord(swapperKeys[i]));
//
//            registerRole(swapperRole);
//
//            issuerRole.addRole(swapperRole);
//        }
////        issuerRole.setMode(ListRole.Mode.ALL);
//
//        registerRole(issuerRole);
//        createRole("owner", issuerRole);
//        createRole("creator", issuerRole);
//    }
//
//    public void addContractToRemove(Contract c) {
//        if( !getRevokingItems().contains(c)) {
//            Binder data = getDefinition().getData();
//            List<Binder> actions = data.getOrCreateList("actions");
//            getRevokingItems().add(c);
//            actions.add(Binder.fromKeysValues("action", "remove", "id", c.getId()));
//        }
//    }

    public static Contract createRevocation(Contract c, PrivateKey... keys) {

        Contract tc = new Contract();

        Definition cd = tc.getDefinition();
        // by default, transactions expire in 30 days
        cd.setExpiresAt(tc.getCreatedAt().plusDays(30));

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : keys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
            tc.addSignerKey(k);
        }
        tc.registerRole(issuerRole);
        tc.createRole("owner", issuerRole);
        tc.createRole("creator", issuerRole);

        if( !tc.getRevokingItems().contains(c)) {
            Binder data = tc.getDefinition().getData();
            List<Binder> actions = data.getOrCreateList("actions");
            tc.getRevokingItems().add(c);
            actions.add(Binder.fromKeysValues("action", "remove", "id", c.getId()));
        }

        tc.seal();

        return tc;
    }

    /**
     * First step of swap procedure. Calls from swapper1 part.
     *
     * Service create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other with asks two signs of swappers
     * and sign contract that was own for calling part.
     *
     * @param contract1 - own for calling part (swapper1) existing contract
     * @param contract2 - foreign for calling part (swapper2) existing contract
     * @param fromKeys - own for calling part (swapper1) private keys
     * @param toKeys - foreign for calling part (swapper2) public keys
     * @return swap contract including new revisions of old contracts swapping between
     */
    public static Contract startSwap(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys) {

        Set<PublicKey> fromPublicKeys = new HashSet<>();
        for (PrivateKey pk : fromKeys) {
            fromPublicKeys.add(pk.getPublicKey());
        }

        // first of all we creating main swap contract which will include new revisions of contract for swap
        // you can think about this contract as about transaction
        Contract swapContract = new Contract();

        Definition cd = swapContract.getDefinition();
        // by default, transactions expire in 30 days
        cd.setExpiresAt(swapContract.getCreatedAt().plusDays(30));

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
        }

        swapContract.registerRole(issuerRole);
        swapContract.createRole("owner", issuerRole);
        swapContract.createRole("creator", issuerRole);

        // now we will prepare new revisions of contracts

        // create new revisions of contracts and create transactional sections in it

        Contract newContract1 = contract1.createRevision(fromKeys);
        Transactional transactional1 = newContract1.createTransactionalSection();
        transactional1.setId(HashId.createRandom().toBase64String());

        Contract newContract2 = contract2.createRevision();
        Transactional transactional2 = newContract2.createTransactionalSection();
        transactional2.setId(HashId.createRandom().toBase64String());


        // prepare roles for references
        // it should new owners and old creators in new revisions of contracts

        SimpleRole ownerFrom = new SimpleRole("owner");
        SimpleRole creatorFrom = new SimpleRole("creator");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            ownerFrom.addKeyRecord(kr);
            creatorFrom.addKeyRecord(kr);
        }

        SimpleRole ownerTo = new SimpleRole("owner");
        SimpleRole creatorTo = new SimpleRole("creator");
        for (PublicKey k : toKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerTo.addKeyRecord(kr);
            creatorTo.addKeyRecord(kr);
        }


        // create references for contracts that point to each other and asks correct signs

        Reference reference1 = new Reference();
        reference1.transactional_id = transactional2.getId();
        reference1.type = Reference.TYPE_TRANSACTIONAL;
        reference1.required = true;
        reference1.signed_by = new ArrayList<>();
        reference1.signed_by.add(ownerFrom);
        reference1.signed_by.add(creatorTo);

        Reference reference2 = new Reference();
        reference2.transactional_id = transactional1.getId();
        reference2.type = Reference.TYPE_TRANSACTIONAL;
        reference2.required = true;
        reference2.signed_by = new ArrayList<>();
        reference2.signed_by.add(ownerTo);
        reference2.signed_by.add(creatorFrom);

        // and add this references to existing transactional section
        transactional1.addReference(reference1);
        transactional2.addReference(reference2);


        // swap owners in this contracts
        newContract1.setOwnerKeys(toKeys);
        newContract2.setOwnerKeys(fromPublicKeys);

        newContract1.seal();
        newContract2.seal();

        // finally on this step add created new revisions to main swap contract
        swapContract.addNewItems(newContract1);
        swapContract.addNewItems(newContract2);
        swapContract.seal();

        return swapContract;
    }

    /**
     * Second step of swap procedure. Calls from swapper2 part.
     *
     * Swapper2 got swap contract from swapper1, give it to service,
     * service sign new contract where calling part was owner, store hashId of this contract.
     * Then add to reference of new contract, that will be own for calling part,
     * contract_id and point it to contract that was own for calling part.
     * Then sign second contract too.
     *
     * @param swapContract - processing swap contract, now got from swapper1
     * @param keys - own (swapper2) private keys
     * @return modified swapContract
     */
    public static Contract signPresentedSwap(Contract swapContract, Set<PrivateKey> keys) {

        Set<PublicKey> publicKeys = new HashSet<>();
        for (PrivateKey pk : keys) {
            publicKeys.add(pk.getPublicKey());
        }

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own and sign it
        HashId contractHashId = null;
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(publicKeys);

            if(willBeMine) {
                c.addSignatureToSeal(keys);
                contractHashId = c.getId();
            }
        }

        // looking for contract that was own, add to reference hash of above contract and sign it
        for (Contract c : swappingContracts) {
            boolean willBeNotMine = (!c.getOwner().isAllowedForKeys(publicKeys));

            if(willBeNotMine) {

                Set<KeyRecord> krs = new HashSet<>();
                for (PublicKey k: publicKeys) {
                    krs.add(new KeyRecord(k));
                }
                c.setCreator(krs);

                if(c.getTransactional() != null && c.getTransactional().getReferences() != null) {
                    for (Reference rm : c.getTransactional().getReferences()) {
                        rm.contract_id = contractHashId;
                    }
                } else {
                    return swapContract;
                }

                c.seal();
                c.addSignatureToSeal(keys);
            }
        }

        swapContract.seal();
        return swapContract;
    }

    /**
     * Third and final step of swap procedure. Calls from swapper1 part.
     *
     * Swapper1 got contracts from swapper2, give it to service and
     * service finally sign contract that will be own for calling part.
     *
     * @param swapContract - processing swap contract, now got back from swapper2
     * @param keys - own (swapper1) private keys
     * @return modified swapContract
     */
    public static Contract finishSwap(Contract swapContract, Set<PrivateKey> keys) {

        List<Contract> swappingContracts = (List<Contract>) swapContract.getNew();

        // looking for contract that will be own
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(keys);

            if(willBeMine) {
                c.addSignatureToSeal(keys);
            }
        }

        swapContract.seal();
        swapContract.addSignatureToSeal(keys);

        return swapContract;
    }

    // for test purposes only



    public static List<Contract> startSwap__WrongSignTest(Contract contract1, Contract contract2, PrivateKey fromKey, PublicKey toKey, PrivateKey wrongKey) {

        List<Contract> swappingContracts = new ArrayList<>();

        Transactional transactional1 = contract1.createTransactionalSection();
        transactional1.setId("" + Do.randomInt(1000000000));

        Transactional transactional2 = contract1.createTransactionalSection();
        transactional2.setId("" + Do.randomInt(1000000000));

        Reference reference1 = new Reference();
        reference1.transactional_id = transactional2.getId();
        reference1.type = Reference.TYPE_TRANSACTIONAL;
        reference1.required = true;
        reference1.signed_by = new ArrayList<>();
        reference1.signed_by.add(new SimpleRole("owner", new KeyRecord(fromKey.getPublicKey())));
        reference1.signed_by.add(new SimpleRole("creator", new KeyRecord(toKey)));
        transactional1.addReference(reference1);

        Reference reference2 = new Reference();
        reference2.transactional_id = transactional1.getId();
        reference2.type = Reference.TYPE_TRANSACTIONAL;
        reference2.required = true;
        reference2.signed_by = new ArrayList<>();
        reference2.signed_by.add(new SimpleRole("owner", new KeyRecord(toKey)));
        reference2.signed_by.add(new SimpleRole("creator", new KeyRecord(fromKey.getPublicKey())));
        transactional2.addReference(reference2);



        Contract newContract1 = contract1.createRevision(transactional1, wrongKey);
        newContract1.setOwnerKeys(toKey);
        newContract1.seal();
        swappingContracts.add(newContract1);

        Contract newContract2 = contract2.createRevision(transactional2);
        newContract2.setOwnerKeys(fromKey.getPublicKey());
        newContract2.seal();
        swappingContracts.add(newContract2);

        return swappingContracts;
    }

    public static List<Contract> signPresentedSwap__WrongSignTest(List<Contract> swappingContracts, PrivateKey key, PrivateKey wrongKey) {

        HashId contractHashId = null;
        for (Contract c : swappingContracts) {
            for (PublicKey k : c.getOwner().getKeys()) {
                if(k.equals(key.getPublicKey())) {

                    c.addSignatureToSeal(wrongKey);
                    contractHashId = c.getId();
                }
            }
        }

        for (Contract c : swappingContracts) {
            boolean isMyContract = false;
            for (PublicKey k : c.getOwner().getKeys()) {
                if(!k.equals(key.getPublicKey())) {
                    isMyContract = true;
                    break;
                }
            }
            if(isMyContract) {

                Set<KeyRecord> krs = new HashSet<>();
                krs.add(new KeyRecord(key.getPublicKey()));
                c.setCreator(krs);

                if(c.getTransactional() != null) {
                    for (Reference rm : c.getTransactional().getReferences()) {
                        rm.contract_id = contractHashId;
                    }
                } else {
                    return swappingContracts;
                }

                c.seal();
                c.addSignatureToSeal(wrongKey);
            }
        }

        return swappingContracts;
    }

    public static List<Contract> finishSwap__WrongSignTest(List<Contract> swappingContracts, PrivateKey key, PrivateKey wrongKey) {

        for (Contract c : swappingContracts) {
            boolean isMyContract = false;
            for (PublicKey k : c.getOwner().getKeys()) {
                if(!k.equals(key.getPublicKey())) {
                    isMyContract = true;
                    break;
                }
            }
            if(!isMyContract) {
                c.addSignatureToSeal(wrongKey);
            }
        }

        return swappingContracts;
    }
}
