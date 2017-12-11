/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionContract extends Contract {
    public TransactionContract(byte[] sealed) throws IOException, Quantiser.QuantiserException {
        super(sealed);
    }

    public TransactionContract() {
        super();
        Definition cd = getDefinition();
        // by default, transactions expire in 30 days
        cd.setExpiresAt(getCreatedAt().plusDays(30));
    }

    /**
     * The only call needed to setup roles and rights and signatures for {@link TransactionContract}.
     * <p>
     * Transaction contract is immutable, and is issued, onwed and created by the same role, so we create it all in one
     * place, with at least one privateKey. Do not change any roles directly.
     *
     * @param issuers
     */
    public void setIssuer(PrivateKey... issuers) {
        SimpleRole issuerRole = new SimpleRole("issuer");
        for (PrivateKey k : issuers) {
            KeyRecord kr = new KeyRecord(k.getPublicKey());
            issuerRole.addKeyRecord(kr);
            addSignerKey(k);
        }
        registerRole(issuerRole);
        createRole("owner", issuerRole);
        createRole("creator", issuerRole);
    }

    public void addContractToRemove(Contract c) {
        if( !getRevokingItems().contains(c)) {
            Binder data = getDefinition().getData();
            List<Binder> actions = data.getOrCreateList("actions");
            getRevokingItems().add(c);
            actions.add(Binder.fromKeysValues("action", "remove", "id", c.getId()));
        }
    }

    public List<Contract> swapOwners(Contract oldContract1, Contract oldContract2) {

        Role role1 = oldContract1.getOwner();
        Role role2 = oldContract2.getOwner();

        Contract newContract1 = oldContract1.createRevision();
        Contract newContract2 = oldContract2.createRevision();

        newContract1.setOwnerKeys(role2.getKeys());
        newContract2.setOwnerKeys(role1.getKeys());

        addNewItems(newContract1, newContract2);
        addContractToRemove(oldContract1);
        addContractToRemove(oldContract2);

        List<Contract> swappedContracts = new ArrayList<>();
        swappedContracts.add(newContract1);
        swappedContracts.add(newContract2);
        return swappedContracts;
    }

    private List<Contract> swappingContracts = new ArrayList<>();
    private List<Contract> swappedContracts = new ArrayList<>();

    public boolean addForSwap(Contract contract) {
        if (swappingContracts.size() < 2) {
            swappingContracts.add(contract);
        } else {
            throw new IllegalArgumentException("You cannot swap more then 2 contracts.");
        }

        // Is swap seats is all busy?
        return swappingContracts.size() == 2;
    }

    public List<Contract> swapOwners() {
        swappedContracts = swapOwners(swappingContracts.get(0), swappingContracts.get(1));
        return swappedContracts;
    }

//    @Override
//    public byte[] seal() {
//        for (Contract c : swappedContracts) {
//            c.seal();
//        }
//        return super.seal();
//    }

    @Override

    public void addSignerKey(PrivateKey privateKey) {

        Set<KeyRecord> krs = new HashSet<>();
        for (Contract c : swappedContracts) {
            krs.add(new KeyRecord(privateKey.getPublicKey()));
            boolean isOwnerKey = false;
            for (PublicKey k : c.getOwner().getKeys()) {
                if(privateKey.getPublicKey().equals(k)) {
                    isOwnerKey = true;
                }
            }
            // we set as creator for this key, if it not belongs to new owner (and belongs to old owner)
            // and sign with it
            if(!isOwnerKey) {
                c.setCreator(krs);
                c.addSignerKey(privateKey);
                c.seal();
            }
        }
        super.addSignerKey(privateKey);
    }

}
