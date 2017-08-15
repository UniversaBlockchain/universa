/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.util.List;

public class TransactionContract extends Contract {
    public TransactionContract(byte[] sealed) throws IOException {
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
        Role issuerRole = new Role("issuer");
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

}
