/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Roman Uskov <roman.uskov@gmail.com>, February 2021.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.*;
import com.icodici.universa.contract.helpers.ManagedToken;
import com.icodici.universa.contract.permissions.*;
import com.icodici.universa.contract.roles.*;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiSerializationException;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Semaphore;

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

        //create root contract
        ManagedToken.MintingRoot mintingRoot = new ManagedToken.MintingRoot(addresses, 2, 3);


        Contract transactionRoot = mintingRoot.getTransaction().getContract();

        //sign transaction
        transactionRoot.addSignatureToSeal(key1);
        transactionRoot.addSignatureToSeal(key2);
        transactionRoot.addSignatureToSeal(key3);

        //register
        transactionRoot.check();
        transactionRoot.traceErrors();
        assertEquals(transactionRoot.getErrors().size(),0);


        //save MintingRoot contract
        byte[] mrPacked = mintingRoot.getContract().getLastSealedBinary();

        //restore MintingRoot from packed
        mintingRoot = new ManagedToken.MintingRoot(mrPacked);


        //create new minting protocol for "uBTC"
        ManagedToken.MintingProtocol mintingProtocol = new ManagedToken.MintingProtocol("uBTC", mintingRoot);

        //get transaction to be registered
        transactionRoot = mintingProtocol.getTransaction().getContract();

        //sign transaction
        transactionRoot.addSignatureToSeal(key1);
        transactionRoot.addSignatureToSeal(key2);
        transactionRoot.addSignatureToSeal(key3);


        //register
        transactionRoot.check();
        transactionRoot.traceErrors();
        assertEquals(transactionRoot.getErrors().size(),0);

        //save protocol contract
        byte[] mpPacked = mintingProtocol.getContract().getLastSealedBinary();
        //save changed root contract
        mrPacked = mintingProtocol.getMintingRoot().getContract().getLastSealedBinary();

        //restore root from packed
        mintingRoot = new ManagedToken.MintingRoot(mrPacked);
        //restore protocol from packed
        mintingProtocol = new ManagedToken.MintingProtocol(mpPacked,mintingRoot);

        //user key
        PrivateKey userKey = TestKeys.privateKey(4);
        KeyAddress userAddress = userKey.getPublicKey().getLongAddress();

        //mint 10000 uBTC
        ManagedToken token = new ManagedToken(new BigDecimal("10000"),mintingProtocol,userAddress);

        //get transaction to be registered
        transactionRoot = token.getTransaction().getContract();

        //sign transaction
        transactionRoot.addSignatureToSeal(key1);
        transactionRoot.addSignatureToSeal(key2);


        transactionRoot.check();
        transactionRoot.traceErrors();
        assertEquals(transactionRoot.getErrors().size(),0);

        //save minted token
        byte[] tokenPacked = token.getContract().getLastSealedBinary();

        //save changed protocol
        mpPacked = token.getMintingProtocol().getContract().getLastSealedBinary();

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


        //restore protocol from packed
        mintingProtocol = new ManagedToken.MintingProtocol(mpPacked,mintingRoot);

        //check issued counter congruence
        assertEquals(mintingProtocol.getContract().getStateData().getString("issued"),"10000");

        //restore ManagedToken from packed
        token = new ManagedToken(regularToken.getLastSealedBinary(),mintingProtocol);

        //revoke the token
        token.revoke();

        //get transaction to be registered
        transactionRoot = token.getTransaction().getContract();

        //sign:
        //1. quorum
        transactionRoot.addSignatureToSeal(key1);
        transactionRoot.addSignatureToSeal(key2);
        //2. token owner
        transactionRoot.addSignatureToSeal(userKey);

        transactionRoot.check();
        transactionRoot.traceErrors();
        assertEquals(transactionRoot.getErrors().size(),0);

        //save changed protocol contract
        mpPacked = token.getMintingProtocol().getContract().getLastSealedBinary();

        //restore protocol from packed
        mintingProtocol = new ManagedToken.MintingProtocol(mpPacked,mintingRoot);

        //check issued counter congruence 10000 - 6000 = 4000
        assertEquals(mintingProtocol.getContract().getStateData().getString("issued"),"4000");

    }


}
