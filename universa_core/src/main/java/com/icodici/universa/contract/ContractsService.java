/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.util.*;

public class ContractsService {

    /**
     * Implementing revoking procedure.
     * Service create temp contract with given contract in revoking items and return it.
     * That temp contract should be send to Universa and given contract will be revoked.
     * @param c - contract should revoked be
     * @param keys - keys from owner of c
     * @return
     */
    public static Contract createRevocation(Contract c, PrivateKey... keys) {

        Contract tc = new Contract();

        Contract.Definition cd = tc.getDefinition();
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
     * Implementing split procedure.
     * Service create new revision of given contract, split it to pair contract with split amount.
     * @param c - contract should split be
     * @param amount - amount that should be split from given contract
     * @param fieldName - name of filed that should be split
     * @param keys - keys from owner of c
     * @return
     */
    public static Contract createSplit(Contract c, long amount, String fieldName, Set<PrivateKey> keys) {
        Contract splitFrom = c.createRevision();
        Contract splitTo = splitFrom.splitValue(fieldName, new Decimal(amount));

        for (PrivateKey key : keys) {
            splitTo.addSignerKey(key);
        }
//        splitTo.createRole("creator", splitTo.getRole("owner"));
        splitTo.seal();
        splitFrom.seal();

        return splitFrom;
    }

    /**
     * Implementing join procedure.
     * Service create new revision of first contract, update amount field with sum of amount fields in both contracts
     * and put second contract in revoking items of created new revision.
     * @param contract1 - contract should be join to
     * @param contract2 - contract should be join
     * @param fieldName - name of field that should be join by
     * @param keys - keys from owner of both contracts
     * @return
     */
    public static Contract createJoin(Contract contract1, Contract contract2, String fieldName, Set<PrivateKey> keys) {
        Contract joinTo = contract1.createRevision();

        joinTo.getStateData().set(
                fieldName,
                getDecimalField(contract1, fieldName).add(getDecimalField(contract2, fieldName))
        );

        for (PrivateKey key : keys) {
            joinTo.addSignerKey(key);
        }
        joinTo.addRevokingItems(contract2);
        joinTo.seal();

        return joinTo;
    }


    public static Contract startSwap(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys) {
        return startSwap(contract1, contract2, fromKeys, toKeys, true);
    }


    public static Contract startSwap(List<Contract> contracts1, List<Contract> contracts2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys) {
        return startSwap(contracts1, contracts2, fromKeys, toKeys, true);
    }

    public static Contract startSwap(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, boolean createNewRevision) {
        List<Contract> contracts1 = new ArrayList<>();
        contracts1.add(contract1);

        List<Contract> contracts2 = new ArrayList<>();
        contracts2.add(contract2);

        return startSwap(contracts1, contracts2, fromKeys, toKeys, createNewRevision);
    }

    /**
     * First step of swap procedure. Calls from swapper1 part.
     *
     * Service create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other with asks two signs of swappers
     * and sign contract that was own for calling part.
     *
     * @param contracts1 - own for calling part (swapper1) existing or new revision of contract
     * @param contracts2 - foreign for calling part (swapper2) existing or new revision contract
     * @param fromKeys - own for calling part (swapper1) private keys
     * @param toKeys - foreign for calling part (swapper2) public keys
     * @param createNewRevision - if true - create new revision of given contracts. If false - use them as new revisions.
     * @return swap contract including new revisions of old contracts swapping between
     */
    public static Contract startSwap(List<Contract> contracts1, List<Contract> contracts2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, boolean createNewRevision) {

        Set<PublicKey> fromPublicKeys = new HashSet<>();
        for (PrivateKey pk : fromKeys) {
            fromPublicKeys.add(pk.getPublicKey());
        }

        // first of all we creating main swap contract which will include new revisions of contract for swap
        // you can think about this contract as about transaction
        Contract swapContract = new Contract();

        Contract.Definition cd = swapContract.getDefinition();
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

        List<Contract> newContracts1 = new ArrayList<>();
        for(Contract c : contracts1) {
            Contract nc;
            if(createNewRevision) {
                nc = c.createRevision(fromKeys);
            } else {
                nc = c;
            }
            nc.createTransactionalSection();
            nc.getTransactional().setId(HashId.createRandom().toBase64String());
            newContracts1.add(nc);
        }

        List<Contract> newContracts2 = new ArrayList<>();
        for(Contract c : contracts2) {
            Contract nc;
            if(createNewRevision) {
                nc = c.createRevision();
            } else {
                nc = c;
            }
            nc.createTransactionalSection();
            nc.getTransactional().setId(HashId.createRandom().toBase64String());
            newContracts2.add(nc);
        }


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
        // and add this references to existing transactional section

        for(Contract nc1 : newContracts1) {
            for(Contract nc2 : newContracts2) {
                Reference reference = new Reference();
                reference.transactional_id = nc2.getTransactional().getId();
                reference.type = Reference.TYPE_TRANSACTIONAL;
                reference.required = true;
                reference.signed_by = new ArrayList<>();
                reference.signed_by.add(ownerFrom);
                reference.signed_by.add(creatorTo);
                nc1.getTransactional().addReference(reference);
            }
        }

        for(Contract nc2 : newContracts2) {
            for (Contract nc1 : newContracts1) {
                Reference reference = new Reference();
                reference.transactional_id = nc1.getTransactional().getId();
                reference.type = Reference.TYPE_TRANSACTIONAL;
                reference.required = true;
                reference.signed_by = new ArrayList<>();
                reference.signed_by.add(ownerTo);
                reference.signed_by.add(creatorFrom);
                nc2.getTransactional().addReference(reference);
            }
        }


        // swap owners in this contracts
        for(Contract nc : newContracts1) {
            nc.setOwnerKeys(toKeys);
            nc.seal();
        }
        for(Contract nc : newContracts2) {
            nc.setOwnerKeys(fromPublicKeys);
            nc.seal();
        }

        // finally on this step add created new revisions to main swap contract
        for(Contract nc : newContracts1) {
            swapContract.addNewItems(nc);
        }
        for(Contract nc : newContracts2) {
            swapContract.addNewItems(nc);
        }
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
        HashMap<String, HashId> contractHashId = new HashMap<>();
        for (Contract c : swappingContracts) {
            boolean willBeMine = c.getOwner().isAllowedForKeys(publicKeys);

            if(willBeMine) {
                c.addSignatureToSeal(keys);
                contractHashId.put(c.getTransactional().getId(), c.getId());
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
                        rm.contract_id = contractHashId.get(rm.transactional_id);
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

    /**
     * Create paid transaction, which consist from contract you want to register and payment contract that will be
     * spend to process transaction.
     * @return
     */
    public synchronized static Parcel createParcel(Contract payload, Contract payment, int amount, Set<PrivateKey> keys) {

        Contract paymentDecreased = payment.createRevision(keys);
        paymentDecreased.getStateData().set("transaction_units", payment.getStateData().getIntOrThrow("transaction_units") - amount);

        // TODO: change to check with issuer
        paymentDecreased.setIsTU(true);

        paymentDecreased.seal();

        Parcel parcel = new Parcel(payload.getTransactionPack(), paymentDecreased.getTransactionPack());

        return parcel;
    }

    public static Decimal getDecimalField(Contract contract, String fieldName) {

        Object valueObject = contract.getStateData().get(fieldName);
        if(valueObject instanceof String) {
            return new Decimal(Integer.valueOf((String) valueObject));
        }

        if(valueObject instanceof Decimal) {
            return (Decimal) valueObject;
        }
        return null;
    }
}
