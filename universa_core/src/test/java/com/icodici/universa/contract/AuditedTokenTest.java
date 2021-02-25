/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Roman Uskov <roman.uskov@gmail.com>, February 2021.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.*;
import com.icodici.universa.contract.helpers.ManagedToken;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;

public class AuditedTokenTest extends ContractTestBase {


    @Test
    public void basicFlow() throws Exception {
        //multisig keys
        PrivateKey key1 = TestKeys.privateKey(1);
        PrivateKey key2 = TestKeys.privateKey(2);
        PrivateKey key3 = TestKeys.privateKey(3);

        Set<KeyAddress> addresses = new HashSet<>();
        addresses.add(key1.getPublicKey().getLongAddress());
        addresses.add(key2.getPublicKey().getLongAddress());
        addresses.add(key3.getPublicKey().getLongAddress());

        /////////////////
        /////////////////
        /////////////////
        //PREPARATION (CENTRALIZED, SERVER SIDE)
        /////////////////
        /////////////////
        /////////////////

        /////////////////
        //REGISTER ROOT
        /////////////////

        //create root contract
        ManagedToken.MintingRoot mintingRoot = new ManagedToken.MintingRoot(addresses, 2, 3);
        Contract latestRootTransaction = mintingRoot.getTransaction().getContract();

        //sign transaction
        latestRootTransaction.addSignatureToSeal(key1,key2,key3);

        //save
        byte[] latestRootPacked = latestRootTransaction.getPackedTransaction();
        //repack
        latestRootTransaction = Contract.fromPackedTransaction(latestRootPacked);

        //register
        latestRootTransaction.getContract().check();
        latestRootTransaction.getContract().traceErrors();
        assertEquals(latestRootTransaction.getContract().getErrors().size(),0);


        /////////////////
        //REGISTER PROTOCOL
        /////////////////

        //create new minting protocol for "uBTC"
        ManagedToken.MintingProtocol mintingProtocol = new ManagedToken.MintingProtocol("uBTC", latestRootPacked);
        latestRootTransaction = mintingProtocol.getTransaction().getContract();

        //sign transaction
        latestRootTransaction.addSignatureToSeal(key1,key2,key3);

        //save
        latestRootPacked = latestRootTransaction.getPackedTransaction();
        //repack
        latestRootTransaction = Contract.fromPackedTransaction(latestRootPacked);
        //register
        latestRootTransaction.getContract().check();
        latestRootTransaction.getContract().traceErrors();
        assertEquals(latestRootTransaction.getContract().getErrors().size(),0);


        /////////////////
        /////////////////
        /////////////////
        //MINTING (PARALLEL, SERVER SIDE)
        /////////////////
        /////////////////
        /////////////////


        //INITIALIZE LOCAL SERVER WITH LATEST TRANSACTION FROM ROOT SERVER
        byte[] latestLocalPacked = latestRootPacked;

        //user key
        PrivateKey userKey = TestKeys.privateKey(4);
        KeyAddress userAddress = userKey.getPublicKey().getLongAddress();

        //mint 10000 uBTC
        ManagedToken token = new ManagedToken(new BigDecimal("10000"), userAddress, latestLocalPacked);
        Contract latestLocalTransaction = token.getTransaction().getContract();

        //sign transaction
        latestLocalTransaction.addSignatureToSeal(key1, key2);

        //save transaction
        latestLocalPacked = latestLocalTransaction.getPackedTransaction();
        //repack
        latestLocalTransaction = Contract.fromPackedTransaction(latestLocalPacked);

        latestLocalTransaction.getContract().check();
        latestLocalTransaction.getContract().traceErrors();
        assertEquals(latestLocalTransaction.getContract().getErrors().size(),0);

        //save minted token
        byte[] tokenPacked = token.getContract().getLastSealedBinary();


        /////////////////
        /////////////////
        /////////////////
        //CLIENT SIDE CODE (PARRALLEL, CLIENT)
        /////////////////
        /////////////////
        /////////////////

        //REGULAR TOKEN OPERATIONS (E.G. SPLIT)
        Contract regularToken = Contract.fromSealedBinary(tokenPacked);
        regularToken = regularToken.createRevision();
        regularToken.setCreatorKeys(userAddress);
        regularToken.splitValue("amount",new Decimal("4000"));
        regularToken.seal();
        regularToken.addSignatureToSeal(userKey);
        regularToken.check();
        regularToken.traceErrors();
        assertEquals(regularToken.getErrors().size(),0);


        //restore ManagedToken from packed
        token = new ManagedToken(regularToken.getLastSealedBinary(),latestLocalPacked);
        //revoke the token
        token.revoke();

        //get transaction to be registered
        latestLocalTransaction = token.getTransaction().getContract();
        latestLocalTransaction.addSignatureToSeal(key1);
        latestLocalTransaction.addSignatureToSeal(key2);
        latestLocalTransaction.addSignatureToSeal(userKey);

        //save
        latestLocalPacked = latestLocalTransaction.getPackedTransaction();
        //repack
        latestLocalTransaction = Contract.fromPackedTransaction(latestLocalPacked);

        //register
        latestLocalTransaction.getContract().check();
        latestLocalTransaction.getContract().traceErrors();
        assertEquals(latestLocalTransaction.getContract().getErrors().size(),0);


        //mint 10000 uBTC
        ManagedToken newToken = new ManagedToken(new BigDecimal("10000"), userAddress, latestLocalPacked);
        latestLocalTransaction = newToken.getTransaction().getContract();

        //sign transaction
        latestLocalTransaction.addSignatureToSeal(key1);
        latestLocalTransaction.addSignatureToSeal(key2);

        //save transaction
        latestLocalPacked = latestLocalTransaction.getPackedTransaction();
        //repack
        latestLocalTransaction = Contract.fromPackedTransaction(latestLocalPacked);

        latestLocalTransaction.getContract().check();
        latestLocalTransaction.getContract().traceErrors();
        assertEquals(latestLocalTransaction.getContract().getErrors().size(),0);

        //IF LOCAL SERVER TRANSACTION FAILS BECASE ROOT CONTRACT CHANGED
        //REQUEST LATEST ROOT TRANSACTION FROM ROOT SERVER
        //latestTransactionRoot = rootServer.GetLatestTransaction()
        //AND make an update
        ManagedToken.updateMintingRootReference(latestLocalTransaction.getTransactionPack(),latestRootPacked);

    }


}
