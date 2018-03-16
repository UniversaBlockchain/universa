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
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import com.icodici.universa.contract.permissions.*;
import java.time.Instant;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

public class ContractsService {

    /**
     * Implementing revoking procedure.
     * <br><br>
     * Service create temp contract with given contract in revoking items and return it.
     * That temp contract should be send to Universa and given contract will be revoked.
     * <br><br>
     * @param c is contract should revoked be
     * @param keys is keys from owner of c
     * @return working contract that should be register in the Universa to finish procedure.
     */
    public synchronized static Contract createRevocation(Contract c, PrivateKey... keys) {

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
     * Implementing split procedure for token-type contracts.
     * <br><br>
     * Service create new revision of given contract, split it to a pair of contracts with split amount.
     * <br><br>
     * Given contract should have splitjoin permission for given keys.
     * <br><br>
     * @param c is contract should split be
     * @param amount is value that should be split from given contract
     * @param fieldName is name of field that should be split
     * @param keys is keys from owner of c
     * @return working contract that should be register in the Universa to finish procedure.
     */
    public synchronized static Contract createSplit(Contract c, long amount, String fieldName, Set<PrivateKey> keys) {
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
     * <br><br>
     * Service create new revision of first contract, update amount field with sum of amount fields in the both contracts
     * and put second contract in revoking items of created new revision.
     * <br><br>
     * Given contract should have splitjoin permission for given keys.
     * <br><br>
     * @param contract1 is contract should be join to
     * @param contract2 is contract should be join
     * @param fieldName is name of field that should be join by
     * @param keys is keys from owner of both contracts
     * @return working contract that should be register in the Universa to finish procedure.
     */
    public synchronized static Contract createJoin(Contract contract1, Contract contract2, String fieldName, Set<PrivateKey> keys) {
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


    /**
     * First step of swap procedure. Calls from swapper1 part.
     *<br><br>
     * Get single contracts.
     *<br><br>
     * Service create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other with asks two signs of swappers
     * and sign contract that was own for calling part.
     *<br><br>
     * Swap procedure consist from three steps:<br>
     * (1) prepare contracts with creating transactional section on the first swapper site, sign only one contract;<br>
     * (2) sign contracts on the second swapper site;<br>
     * (3) sign lost contracts on the first swapper site and finishing swap.
     *<br><br>
     * @param contract1 is own for calling part (swapper1 owned), existing or new revision of contract
     * @param contract2 is foreign for calling part (swapper2 owned), existing or new revision contract
     * @param fromKeys is own for calling part (swapper1 keys) private keys
     * @param toKeys is foreign for calling part (swapper2 keys) public keys
     * @return swap contract including new revisions of old contracts swapping between;
     * should be send to partner (swapper2) and he should go to step (2) of the swap procedure.
     */
    public synchronized static Contract startSwap(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys) {
        return startSwap(contract1, contract2, fromKeys, toKeys, true);
    }


    /**
     * First step of swap procedure. Calls from swapper1 part.
     *<br><br>
     * Get lists of contracts.
     *<br><br>
     * Service create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other with asks two signs of swappers
     * and sign contract that was own for calling part.
     *<br><br>
     * Swap procedure consist from three steps:<br>
     * (1) prepare contracts with creating transactional section on the first swapper site, sign only one contract;<br>
     * (2) sign contracts on the second swapper site;<br>
     * (3) sign lost contracts on the first swapper site and finishing swap.
     *<br><br>
     * @param contracts1 is list of own for calling part (swapper1 owned), existing or new revision of contract
     * @param contracts2 is list of foreign for calling part (swapper2 owned), existing or new revision contract
     * @param fromKeys is own for calling part (swapper1 keys) private keys
     * @param toKeys is foreign for calling part (swapper2 keys) public keys
     * @return swap contract including new revisions of old contracts swapping between;
     * should be send to partner (swapper2) and he should go to step (2) of the swap procedure.
     */
    public synchronized static Contract startSwap(List<Contract> contracts1, List<Contract> contracts2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys) {
        return startSwap(contracts1, contracts2, fromKeys, toKeys, true);
    }


    /**
     * First step of swap procedure. Calls from swapper1 part.
     *<br><br>
     * Get single contracts.
     *<br><br>
     * Service create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other with asks two signs of swappers
     * and sign contract that was own for calling part.
     *<br><br>
     * Swap procedure consist from three steps:<br>
     * (1) prepare contracts with creating transactional section on the first swapper site, sign only one contract;<br>
     * (2) sign contracts on the second swapper site;<br>
     * (3) sign lost contracts on the first swapper site and finishing swap.
     *<br><br>
     * @param contract1 is own for calling part (swapper1 owned), existing or new revision of contract
     * @param contract2 is foreign for calling part (swapper2 owned), existing or new revision contract
     * @param fromKeys is own for calling part (swapper1 keys) private keys
     * @param toKeys is foreign for calling part (swapper2 keys) public keys
     * @param createNewRevision if true - create new revision of given contracts. If false - use them as new revisions.
     * @return swap contract including new revisions of old contracts swapping between;
     * should be send to partner (swapper2) and he should go to step (2) of the swap procedure.
     */
    public synchronized static Contract startSwap(Contract contract1, Contract contract2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, boolean createNewRevision) {
        List<Contract> contracts1 = new ArrayList<>();
        contracts1.add(contract1);

        List<Contract> contracts2 = new ArrayList<>();
        contracts2.add(contract2);

        return startSwap(contracts1, contracts2, fromKeys, toKeys, createNewRevision);
    }


    /**
     * First step of swap procedure. Calls from swapper1 part.
     *<br><br>
     * Get lists of contracts.
     *<br><br>
     * Service create new revisions of existing contracts, change owners,
     * added transactional sections with references to each other with asks two signs of swappers
     * and sign contract that was own for calling part.
     *<br><br>
     * Swap procedure consist from three steps:<br>
     * (1) prepare contracts with creating transactional section on the first swapper site, sign only one contract;<br>
     * (2) sign contracts on the second swapper site;<br>
     * (3) sign lost contracts on the first swapper site and finishing swap.
     *<br><br>
     * @param contracts1 is list of own for calling part (swapper1 owned), existing or new revision of contract
     * @param contracts2 is list of foreign for calling part (swapper2 owned), existing or new revision contract
     * @param fromKeys is own for calling part (swapper1 keys) private keys
     * @param toKeys is foreign for calling part (swapper2 keys) public keys
     * @param createNewRevision if true - create new revision of given contracts. If false - use them as new revisions.
     * @return swap contract including new revisions of old contracts swapping between;
     * should be send to partner (swapper2) and he should go to step (2) of the swap procedure.
     */
    public synchronized static Contract startSwap(List<Contract> contracts1, List<Contract> contracts2, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, boolean createNewRevision) {

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
     *<br><br>
     * Swapper2 got swap contract from swapper1 and give it to service.
     * Service sign new contract where calling part was owner, store hashId of this contract.
     * Then add to reference of new contract, that will be own for calling part,
     * contract_id and point it to contract that was own for calling part.
     * Then sign second contract too.
     *<br><br>
     * Swap procedure consist from three steps:<br>
     * (1) prepare contracts with creating transactional section on the first swapper site, sign only one contract;<br>
     * (2) sign contracts on the second swapper site;<br>
     * (3) sign lost contracts on the first swapper site and finishing swap.
     *<br><br>
     * @param swapContract is being processing swap contract, got from swapper1
     * @param keys is own (belongs to swapper2) private keys
     * @return modified swapContract;
     * should be send back to partner (swapper1) and he should go to step (3) of the swap procedure.
     */
    public synchronized static Contract signPresentedSwap(Contract swapContract, Set<PrivateKey> keys) {

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
     *<br><br>
     * Swapper1 got swap contract from swapper2, give it to service and
     * service finally sign contract (that is inside swap contract) that will be own for calling part.
     *<br><br>
     * Swap procedure consist from three steps:<br>
     * (1) prepare contracts with creating transactional section on the first swapper site, sign only one contract;<br>
     * (2) sign contracts on the second swapper site;<br>
     * (3) sign lost contracts on the first swapper site and finishing swap.
     *<br><br>
     * @param swapContract is being processing swap contract, now got back from swapper2
     * @param keys is own (belongs to swapper1) private keys
     * @return ready and sealed swapContract that should be register in the Universa to finish procedure.
     */
    public synchronized static Contract finishSwap(Contract swapContract, Set<PrivateKey> keys) {

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
     * Creates a contract with two signatures.
     *<br><br>
     * The service creates a contract which asks two signatures.
     * It can not be registered without both parts of deal, so it is make sure both parts that they agreed with contract.
     * Service creates a contract that should be send to partner,
     * then partner should sign it and return back for final sign from calling part.
     *<br><br>
     * @param BaseContract is base contract
     * @param fromKeys is own private keys
     * @param toKeys is foreign public keys
     * @param createNewRevision create new revision if true
     * @return contract with two signatures that should be send from first part to partner.
     */
    public synchronized static Contract createTwoSignedContract(Contract BaseContract, Set<PrivateKey> fromKeys, Set<PublicKey> toKeys, boolean createNewRevision) {

        Contract twoSignContract = BaseContract;

        if (createNewRevision) {
            twoSignContract = BaseContract.createRevision(fromKeys);
            twoSignContract.getKeysToSignWith().clear();
        }

        SimpleRole creatorFrom = new SimpleRole("creator");
        for (PrivateKey k : fromKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            creatorFrom.addKeyRecord(kr);
        }

        SimpleRole ownerTo = new SimpleRole("owner");
        for (PublicKey k : toKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerTo.addKeyRecord(kr);
        }

        twoSignContract.createTransactionalSection();
        twoSignContract.getTransactional().setId(HashId.createRandom().toBase64String());

        Reference reference = new Reference();
        reference.transactional_id = twoSignContract.getTransactional().getId();
        reference.type = Reference.TYPE_TRANSACTIONAL;
        reference.required = true;
        reference.signed_by = new ArrayList<>();
        reference.signed_by.add(creatorFrom);
        reference.signed_by.add(ownerTo);
        twoSignContract.getTransactional().addReference(reference);

        twoSignContract.setOwnerKeys(toKeys);
        twoSignContract.seal();

        return twoSignContract;
    }

    /**
     * Creates a token contract.
     *<br><br>
     * The service creates a token contract.
     *<br><br>
     * @param issuerKeys is issuer private keys.
     * @param ownerKeys is owner public keys.
     * @param amount is amount transaction units.
     * @return signed and sealed contract, ready for register.
     */
    public synchronized static Contract createTokenContract(Set<PrivateKey> issuerKeys, Set<PublicKey> ownerKeys, String amount){
        Contract tokenContract = new Contract();
        tokenContract.setApiLevel(3);

        Contract.Definition cd = tokenContract.getDefinition();
        cd.setExpiresAt(ZonedDateTime.ofInstant(Instant.ofEpochSecond(1659720337), ZoneOffset.UTC));

        Binder data = new Binder();
        data.set("name", "Token name");
        data.set("currency_code", "TKN");
        data.set("currency_name", "Token name");
        data.set("description", "Token description.");
        cd.setData(data);

        SimpleRole revokeRole = new SimpleRole("revoke_role");

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : issuerKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
            revokeRole.addKeyRecord(kr);
        }

        SimpleRole ownerRole = new SimpleRole("owner");
        for (PublicKey k : ownerKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerRole.addKeyRecord(kr);
            revokeRole.addKeyRecord(kr);
        }

        tokenContract.registerRole(issuerRole);
        tokenContract.createRole("issuer", issuerRole);
        tokenContract.createRole("creator", issuerRole);
        tokenContract.getStateData().set("amount", amount);

        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);
        tokenContract.addPermission(changeOwnerPerm);

        Binder params = new Binder();
        params.set("min_value", 0.01);
        params.set("min_unit", 0.001);
        params.set("field_name", "amount");
        params.set("join_match_fields", "state.origin");

        SplitJoinPermission splitJoinPerm = new SplitJoinPermission(ownerRole, params);
        tokenContract.addPermission(splitJoinPerm);

        RevokePermission revokePerm = new RevokePermission(revokeRole);
        tokenContract.addPermission(revokePerm);

        tokenContract.setOwnerKeys(ownerKeys);

        tokenContract.seal();
        tokenContract.addSignatureToSeal(issuerKeys);

        return tokenContract;
    }


    /**
     * Creates a share contract.
     *<br><br>
     * The service creates a share contract.
     *<br><br>
     * @param issuerKeys is issuer private keys.
     * @param ownerKeys is owner public keys.
     * @param amount is amount transaction units.
     * @return signed and sealed contract, ready for register.
     */
    public synchronized static Contract createShareContract(Set<PrivateKey> issuerKeys, Set<PublicKey> ownerKeys, String amount){
        Contract shareContract = new Contract();
        shareContract.setApiLevel(3);

        Contract.Definition cd = shareContract.getDefinition();
        cd.setExpiresAt(ZonedDateTime.ofInstant(Instant.ofEpochSecond(1659720337), ZoneOffset.UTC));

        Binder data = new Binder();
        data.set("name", "Share name");
        data.set("currency_code", "SHN");
        data.set("currency_name", "Share name");
        data.set("description", "Share description.");
        cd.setData(data);

        SimpleRole revokeRole = new SimpleRole("revoke_role");

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : issuerKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
            revokeRole.addKeyRecord(kr);
        }

        SimpleRole ownerRole = new SimpleRole("owner");
        for (PublicKey k : ownerKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerRole.addKeyRecord(kr);
            revokeRole.addKeyRecord(kr);
        }

        shareContract.registerRole(issuerRole);
        shareContract.createRole("issuer", issuerRole);
        shareContract.createRole("creator", issuerRole);
        shareContract.getStateData().set("amount", amount);

        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);
        shareContract.addPermission(changeOwnerPerm);

        Binder params = new Binder();
        params.set("min_value", 1);
        params.set("min_unit", 1);
        params.set("field_name", "amount");
        params.set("join_match_fields", "state.origin");

        SplitJoinPermission splitJoinPerm = new SplitJoinPermission(ownerRole, params);
        shareContract.addPermission(splitJoinPerm);

        RevokePermission revokePerm = new RevokePermission(revokeRole);
        shareContract.addPermission(revokePerm);

        shareContract.setOwnerKeys(ownerKeys);

        shareContract.seal();
        shareContract.addSignatureToSeal(issuerKeys);

        return shareContract;
    }


    /**
     * Creates a notary contract.
     *<br><br>
     * The service creates a notary contract.
     *<br><br>
     * @param issuerKeys is issuer private keys.
     * @param ownerKeys is owner public keys.
     * @return signed and sealed contract, ready for register.
     */
    public synchronized static Contract createNotaryContract(Set<PrivateKey> issuerKeys, Set<PublicKey> ownerKeys){
        Contract notaryContract = new Contract();
        notaryContract.setApiLevel(3);

        Contract.Definition cd = notaryContract.getDefinition();
        cd.setExpiresAt(ZonedDateTime.ofInstant(Instant.ofEpochSecond(1659720337), ZoneOffset.UTC));

        Binder data = new Binder();
        data.set("name", "Notary");
        data.set("description", "This contract represents the notary.");
        cd.setData(data);

        SimpleRole revokeRole = new SimpleRole("revoke_role");

        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : issuerKeys) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
            revokeRole.addKeyRecord(kr);
        }

        SimpleRole ownerRole = new SimpleRole("owner");
        for (PublicKey k : ownerKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerRole.addKeyRecord(kr);
            revokeRole.addKeyRecord(kr);
        }

        notaryContract.registerRole(issuerRole);
        notaryContract.createRole("issuer", issuerRole);
        notaryContract.createRole("creator", issuerRole);

        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);
        notaryContract.addPermission(changeOwnerPerm);

        RevokePermission revokePerm = new RevokePermission(revokeRole);
        notaryContract.addPermission(revokePerm);

        notaryContract.setOwnerKeys(ownerKeys);

        notaryContract.seal();
        notaryContract.addSignatureToSeal(issuerKeys);

        return notaryContract;
    }


    /**
     * Create paid transaction, which consist from contract you want to register and payment contract that will be
     * spend to process transaction.
     *<br><br>
     * @param payload is prepared contract you want to register in the Universa.
     * @param payment is approved contract with transaction units belongs to you.
     * @param amount is number of transaction units you want to spend to register payload contract.
     * @param keys is own private keys, which are set as owner of payment contract
     * @return parcel, it ready to send to the Universa.
     */
    public synchronized static Parcel createParcel(Contract payload, Contract payment, int amount, Set<PrivateKey> keys) {

        Contract paymentDecreased = payment.createRevision(keys);
        paymentDecreased.getStateData().set("transaction_units", payment.getStateData().getIntOrThrow("transaction_units") - amount);

        paymentDecreased.seal();

        Parcel parcel = new Parcel(payload.getTransactionPack(), paymentDecreased.getTransactionPack());

        return parcel;
    }


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

        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(Config.tuKeyPath));
        Contract tu = Contract.fromDslFile(Config.tuTemplatePath);

        SimpleRole ownerRole = new SimpleRole("owner");
        for (PublicKey k : ownerKeys) {
            KeyRecord kr = new KeyRecord(k);
            ownerRole.addKeyRecord(kr);
        }

        tu.registerRole(ownerRole);
        tu.createRole("owner", ownerRole);

        tu.getStateData().set("transaction_units", amount);

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
    private static Decimal getDecimalField(Contract contract, String fieldName) {

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
