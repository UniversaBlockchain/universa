/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionContract extends Contract {

    public TransactionContract(byte[] sealed) throws IOException {
        super(sealed);
    }

    public TransactionContract(byte[] sealed, TransactionPack pack) throws IOException {
        super(sealed, pack);
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

    public void setIssuer(PublicKey... swapperKeys) {
        ListRole issuerRole = new ListRole("issuer");
        for (int i = 0; i < swapperKeys.length; i++) {
            SimpleRole swapperRole = new SimpleRole("swapper" + (i+1));

            swapperRole.addKeyRecord(new KeyRecord(swapperKeys[i]));

            registerRole(swapperRole);

            issuerRole.addRole(swapperRole);
        }
//        issuerRole.setMode(ListRole.Mode.ALL);

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

    public void addForSwap(Contract contract, PrivateKey fromKey, PublicKey toKey) {

        Contract newContract = contract.createRevision(fromKey);
        newContract.setOwnerKeys(toKey);
        addContractToRemove(contract);
        addNewItems(newContract);
        newContract.seal();
    }
}
