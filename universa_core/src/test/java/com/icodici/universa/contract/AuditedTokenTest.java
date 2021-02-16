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
        //KEYS
        PrivateKey key1 = TestKeys.privateKey(1);
        PrivateKey key2 = TestKeys.privateKey(2);
        PrivateKey key3 = TestKeys.privateKey(3);
        //////////////////////
        //////////////////////
        //MULTISIG CONTRACT
        //////////////////////
        //////////////////////
        Contract multisigContract = new Contract();
        multisigContract.setExpiresAt(ZonedDateTime.now().plusYears(1000));
        ArrayList<KeyAddress> addresses = new ArrayList<KeyAddress>();
        addresses.add(key1.getPublicKey().getLongAddress());
        addresses.add(key2.getPublicKey().getLongAddress());
        addresses.add(key3.getPublicKey().getLongAddress());

        //MULTISIG ADDRESSES
        multisigContract.getStateData().put("addresses", addresses);
        //MULTISIG QUORUM
        multisigContract.getStateData().put("quorum", 2);
        //STRONG MULTISIG QUORUM
        multisigContract.getStateData().put("strong_quorum", 3);

        //ISSUER, OWNER, CREATOR
        QuorumVoteRole issuerRole = new QuorumVoteRole("issuer",multisigContract,"this.state.data.addresses","this.state.data.strong_quorum");
        multisigContract.addRole(issuerRole);
        multisigContract.addRole(new RoleLink("owner",multisigContract,"issuer"));
        multisigContract.addRole(new RoleLink("creator",multisigContract,"issuer"));

        //ISSUE COUNTER
        QuorumVoteRole issueCounter = new QuorumVoteRole("issue_counter",multisigContract,"this.state.data.addresses","this.state.data.strong_quorum");
        issueCounter.addRequiredReference("ref_parent",Role.RequiredMode.ALL_OF);
        issueCounter.addRequiredReference("ref_counter",Role.RequiredMode.ALL_OF);
        multisigContract.addRole(issueCounter);

        Reference parentReference = new Reference(multisigContract);
        parentReference.name = "ref_parent";
        parentReference.setConditions(Binder.of("all_of",Do.listOf(
                "ref.id==this.state.parent"
        )));
        multisigContract.addReference(parentReference);

        Reference childReference = new Reference(multisigContract);
        childReference.name = "ref_child";
        childReference.setConditions(Binder.of("all_of",Do.listOf(
                "ref.state.parent==this.id"
        )));
        multisigContract.addReference(childReference);

        Reference counterReference = new Reference(multisigContract);
        counterReference.name = "ref_counter";
        counterReference.setConditions(Binder.of("all_of",Do.listOf(
                "ref_parent.id in this.revokingItems", //ensure we are registering new revision within currenct transaction
                "ref_parent.state.data.counters_issued::number+1==this.state.data.counters_issued::number", //ensure we increase counters counter )
                "ref.id in this.newItems", //counter is in new items
                //"size(this.newItems)==1",
                "ref.definition.data.multisig_origin==this.origin", //points to this
                "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
        )));
        multisigContract.addReference(counterReference);


        Reference counterReferenceChnp = new Reference(multisigContract);
        counterReferenceChnp.name = "ref_counter_chnp";
        counterReferenceChnp.setConditions(Binder.of("all_of",Do.listOf(
                "this.id in ref_child.revokingItems", //ensure we are registering new revision within currenct transaction
                "this.state.data.counters_issued::number+1==ref_child.state.data.counters_issued::number", //ensure we increase counters counter )
                "ref.id in ref_child.newItems", //counter is in new items
                //"size(ref_child.newItems)==1",
                "ref.definition.data.multisig_origin==this.origin", //points to this
                "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
        )));
        multisigContract.addReference(counterReferenceChnp);

        SimpleRole expectedCounterIssuerRole = new SimpleRole("expected_counter_issuer",multisigContract);
        expectedCounterIssuerRole.addRequiredReference("ref_issue_counter",Role.RequiredMode.ALL_OF);
        multisigContract.addRole(expectedCounterIssuerRole);

        Reference expectedCounterIssuerRef = new Reference(multisigContract);
        expectedCounterIssuerRef.name = "expected_counter_issuer";
        expectedCounterIssuerRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref.newItems",
                "this can_perform ref.state.roles.issue_counter"
        )));
        multisigContract.addReference(expectedCounterIssuerRef);


        SimpleRole expectedCoinIssuerRole = new SimpleRole("expected_coin_issuer",multisigContract);
        expectedCoinIssuerRole.addRequiredReference("ref_counter_rev", Role.RequiredMode.ALL_OF);
        expectedCoinIssuerRole.addRequiredReference("ref_coin_issuer",Role.RequiredMode.ALL_OF);
        multisigContract.addRole(expectedCoinIssuerRole);

        Reference expectedCoinIssuerRef = new Reference(multisigContract);
        expectedCoinIssuerRef.name = "expected_coin_issuer";
        expectedCoinIssuerRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref_counter_rev.newItems",
                "this can_perform ref.state.roles.issue_coin"
        )));
        multisigContract.addReference(expectedCoinIssuerRef);


        ChangeOwnerPermission expectedChown = new ChangeOwnerPermission(new RoleLink("@chown",multisigContract,"owner"));
        expectedChown.setId("expected_chown");
        multisigContract.addPermission(expectedChown);

        RoleLink expectedRevokeRoleCoin = new RoleLink("@revoke", multisigContract, "owner");
        expectedRevokeRoleCoin.addRequiredReference("revoke_ref", Role.RequiredMode.ALL_OF);
        RevokePermission expectedRevoke = new RevokePermission(expectedRevokeRoleCoin);
        expectedRevoke.setId("expected_revoke");
        multisigContract.addPermission(expectedRevoke);

        Reference expectedRevokeRefCoin = new Reference(multisigContract);
        expectedRevokeRefCoin.name = "expected_revoke_ref";
        expectedRevokeRefCoin.setConditions(Binder.of("all_of",Do.listOf("this.id in ref_counter_rev.revokingItems")));
        multisigContract.addReference(expectedRevokeRefCoin);

        SplitJoinPermission expectedSjp = new SplitJoinPermission(new RoleLink("@chown",multisigContract,"owner"),Binder.of(
                "field_name", "amount",
                "join_match_fields", Do.listOf(
                        "issuer",
                        "definition.references.ref_counter_rev",
                        "definition.references.ref_coin_issuer",
                        "definition.data.coin_code",
                        "definition.data.multisig_origin",
                        "definition.permisisons.revoke",
                        "definition.references.revoke_ref"
                )
        ));
        expectedSjp.setId("expected_sjp");
        multisigContract.addPermission(expectedSjp);


        SimpleRole dummyMultisig = new SimpleRole("dummy",multisigContract);
        dummyMultisig.addRequiredReference("expected_counter_issuer",Role.RequiredMode.ALL_OF);
        dummyMultisig.addRequiredReference("expected_coin_issuer",Role.RequiredMode.ALL_OF);
        dummyMultisig.addRequiredReference("expected_coin_counter_rev",Role.RequiredMode.ALL_OF);
        dummyMultisig.addRequiredReference("expected_revoke_ref",Role.RequiredMode.ALL_OF);


        multisigContract.addRole(dummyMultisig);

        //MODIFY QUORUMS PERMISSION
        QuorumVoteRole updateQuorumRole = new QuorumVoteRole("update_quorum",multisigContract,"this.state.data.addresses","this.state.data.strong_quorum");
        multisigContract.addRole(updateQuorumRole);

        ModifyDataPermission mdpMultisig = new ModifyDataPermission(new RoleLink("@mdp",multisigContract,"update_quorum"),Binder.of("fields",Binder.of(
                "addresses",null,
                "quorum",null,
                "strong_quorum",null
                )));
        multisigContract.addPermission(mdpMultisig);

        QuorumVoteRole chnpRoleMultisig = new QuorumVoteRole("@chnp",multisigContract,"this.state.data.addresses","this.state.data.strong_quorum");
        chnpRoleMultisig.addRequiredReference("ref_child", Role.RequiredMode.ALL_OF);
        chnpRoleMultisig.addRequiredReference("ref_counter_chnp", Role.RequiredMode.ALL_OF);
        //COUNTER CONTRACTS ISSUED COUNT
        ChangeNumberPermission chnpMultisig = new ChangeNumberPermission(chnpRoleMultisig,Binder.of(
                "min_step","1",
                "max_step","1",
                "field_name","counters_issued"
        ));
        multisigContract.addPermission(chnpMultisig);
        multisigContract.getStateData().put("counters_issued",0);


        Reference counterRevReference = new Reference(multisigContract);
        counterRevReference.name = "ref_counter_rev";
        counterRevReference.setConditions(Binder.of("all_of",Do.listOf(
                "ref.state.parent in ref.revokingItems", //
                "ref.definition.data.multisig_origin==this.origin", //points to this
                "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
        )));
        multisigContract.addReference(counterRevReference);

        Reference expectedCoinCounterRevReference = new Reference(multisigContract);
        expectedCoinCounterRevReference.name = "expected_coin_counter_rev";
        expectedCoinCounterRevReference.setConditions(Binder.of("all_of",Do.listOf(
                "ref.state.parent in ref.revokingItems", //
                "ref.definition.data.multisig_origin==this.definition.data.multisig_origin", //points to this
                "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
        )));
        multisigContract.addReference(expectedCoinCounterRevReference);

        Reference counterRevParentReference = new Reference(multisigContract);
        counterRevParentReference.name = "ref_counter_rev_parent";
        counterRevParentReference.setConditions(Binder.of("all_of",Do.listOf(
                "ref.id==ref_counter_rev.state.parent"
        )));
        multisigContract.addReference(counterRevParentReference);

        Reference tokenRef = new Reference(multisigContract);
        tokenRef.name = "ref_token";
        tokenRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.definition.data.coin_code==ref_counter_rev.definition.data.coin_code",
                "ref.issuer==this.state.roles.expected_coin_issuer",
                "ref.definition.references.ref_coin_issuer==this.definition.references.expected_coin_issuer",
                "ref.definition.references.ref_counter_rev==this.definition.references.expected_coin_counter_rev",
                "ref.definition.data.multisig_origin==this.origin",
                "ref.state.roles.expected_counter_issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.expected_counter_issuer==this.definition.references.expected_counter_issuer", //expected ref in issuer
                //"size(ref.definition.permissions)==3",
                "ref.definition.references.revoke_ref==this.definition.references.expected_revoke_ref",
                "ref.definition.permissions.chown==this.definition.permissions.expected_chown",
                "ref.definition.permissions.sjp==this.definition.permissions.expected_sjp",
                "ref.definition.permissions.revoke==this.definition.permissions.expected_revoke"

        )));
        multisigContract.addReference(tokenRef);


        Reference issuedCongruenceRef = new Reference(multisigContract);
        issuedCongruenceRef.name = "ref_issued_congruence";
        issuedCongruenceRef.setConditions(Binder.of("any_of",Do.listOf(
                Binder.of("all_of", Do.listOf(
                        "ref_token.id in ref_counter_rev.newItems",
                        //"size(ref_counter_rev.newItems)==1",
                        //"size(ref_counter_rev.revokingItems)==1",
                        "ref_counter_rev.state.data.issued::number-ref_counter_rev_parent.state.data.issued::number==ref_token.state.data.amount::number"
                )),
                Binder.of("all_of", Do.listOf(
                        "ref_token.id in ref_counter_rev.revokingItems",
                        //"size(ref_counter_rev.newItems)==0",
                        //"size(ref_counter_rev.revokingItems)==2",
                        "ref_counter_rev_parent.state.data.issued::number-ref_counter_rev.state.data.issued::number==ref_token.state.data.amount::number"
                ))
        )));
        multisigContract.addReference(issuedCongruenceRef);


        QuorumVoteRole issueCoinRole = new QuorumVoteRole("issue_coin",multisigContract,"this.state.data.addresses","this.state.data.quorum");
        issueCoinRole.addRequiredReference("ref_token",Role.RequiredMode.ANY_OF);
        issueCoinRole.addRequiredReference("ref_counter_rev",Role.RequiredMode.ALL_OF);
        issueCoinRole.addRequiredReference("ref_counter_rev_parent",Role.RequiredMode.ALL_OF);
        issueCoinRole.addRequiredReference("ref_issued_congruence",Role.RequiredMode.ALL_OF);
        multisigContract.addRole(issueCoinRole);

        multisigContract.seal();
        multisigContract.addSignatureToSeal(key1);
        multisigContract.addSignatureToSeal(key2);
        multisigContract.addSignatureToSeal(key3);

        multisigContract.check();
        multisigContract.traceErrors();
        assertEquals(multisigContract.getErrors().size(),0);




        //ISSUE NEW COUNTER

        multisigContract = multisigContract.createRevision();
        multisigContract.getStateData().put("counters_issued",1);

        //COUNTER CONTRACT
        Contract counterContract = new Contract();
        counterContract.setExpiresAt(ZonedDateTime.now().plusYears(1000));
        counterContract.getDefinition().getData().put("multisig_origin",multisigContract.getOrigin());
        counterContract.getDefinition().getData().put("coin_code","uBTC");

        SimpleRole counterIssuer = new SimpleRole("issuer",counterContract);
        counterIssuer.addRequiredReference("ref_issue_counter",Role.RequiredMode.ALL_OF);
        counterContract.addRole(counterIssuer);

        Reference counterIssueCounterRef = new Reference(counterContract);
        counterIssueCounterRef.name = "ref_issue_counter";
        counterIssueCounterRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref.newItems",
                "this can_perform ref.state.roles.issue_counter"
        )));
        counterContract.addReference(counterIssueCounterRef);




        counterContract.addRole(new RoleLink("owner",counterContract,"issuer"));
        counterContract.addRole(new RoleLink("creator",counterContract,"issuer"));

        counterContract.getStateData().put("issued","0");

        ModifyDataPermission mdpCounter = new ModifyDataPermission(new RoleLink("@mdp",counterContract,"issue_coin"),Binder.of("fields",Binder.of(
                "issued",null
                )));
        counterContract.addPermission(mdpCounter);


        SimpleRole issueCoinRoleCounter = new SimpleRole("issue_coin",counterContract);
        issueCoinRoleCounter.addRequiredReference("ref_issue_coin",Role.RequiredMode.ALL_OF);
        counterContract.addRole(issueCoinRoleCounter);

        Reference issueCoinRef = new Reference(counterContract);
        issueCoinRef.name = "ref_issue_coin";
        issueCoinRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this can_perform ref.state.roles.issue_coin"
        )));
        counterContract.addReference(issueCoinRef);

        

        counterContract.seal();

        multisigContract.addNewItems(counterContract);
        multisigContract.seal();
        multisigContract.addSignatureToSeal(key1);
        multisigContract.addSignatureToSeal(key2);
        multisigContract.addSignatureToSeal(key3);

        multisigContract.check();
        multisigContract.traceErrors();
        assertEquals(multisigContract.getErrors().size(),0);


        counterContract = counterContract.createRevision();
        counterContract.addRole(new RoleLink("creator",counterContract,"issue_coin"));
        counterContract.getStateData().put("issued","1000");


        PrivateKey userKey = TestKeys.privateKey(4);
        Contract coinContract = new Contract();
        coinContract.setExpiresAt(ZonedDateTime.now().plusYears(1000));
        SimpleRole issuerCoin = new SimpleRole("issuer",coinContract);
        issuerCoin.addRequiredReference("ref_counter_rev", Role.RequiredMode.ALL_OF);
        issuerCoin.addRequiredReference("ref_coin_issuer", Role.RequiredMode.ALL_OF);
        coinContract.addRole(issuerCoin);
        coinContract.setOwnerKey(userKey.getPublicKey().getLongAddress());
        coinContract.addRole(new RoleLink("creator",coinContract,"issuer"));

        ChangeOwnerPermission chown = new ChangeOwnerPermission(new RoleLink("@chown",coinContract,"owner"));
        chown.setId("chown");
        coinContract.addPermission(chown);

        RoleLink revokeRole = new RoleLink("@revoke", coinContract, "owner");
        revokeRole.addRequiredReference("revoke_ref", Role.RequiredMode.ALL_OF);
        RevokePermission revoke = new RevokePermission(revokeRole);
        revoke.setId("revoke");
        coinContract.addPermission(revoke);

        Reference revokeRefCoin = new Reference(coinContract);
        revokeRefCoin.name = "revoke_ref";
        revokeRefCoin.setConditions(Binder.of("all_of",Do.listOf("this.id in ref_counter_rev.revokingItems")));
        coinContract.addReference(revokeRefCoin);

        SplitJoinPermission sjp = new SplitJoinPermission(new RoleLink("@chown",coinContract,"owner"),Binder.of(
           "field_name", "amount",
           "join_match_fields", Do.listOf(
                   "issuer",
                        "definition.references.ref_counter_rev",
                        "definition.references.ref_coin_issuer",
                        "definition.data.coin_code",
                        "definition.data.multisig_origin",
                        "definition.permisisons.revoke",
                        "definition.references.revoke_ref"
                )
        ));
        sjp.setId("sjp");
        coinContract.addPermission(sjp);


        Reference coinIssuerRef = new Reference(coinContract);
        coinIssuerRef.name = "ref_coin_issuer";
        coinIssuerRef.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref_counter_rev.newItems",
                "this can_perform ref.state.roles.issue_coin"
        )));
        coinContract.addReference(coinIssuerRef);

        Reference counterRevReferenceCoin = new Reference(coinContract);
        counterRevReferenceCoin.name = "ref_counter_rev";
        counterRevReferenceCoin.setConditions(Binder.of("all_of",Do.listOf(
                "ref.state.parent in ref.revokingItems", //
                "ref.definition.data.multisig_origin==this.definition.data.multisig_origin", //points to this
                "ref.issuer==this.state.roles.expected_counter_issuer", //has expected issuer
                "ref.definition.references.ref_issue_counter==this.definition.references.expected_counter_issuer" //expected ref in issuer
        )));
        coinContract.addReference(counterRevReferenceCoin);

        SimpleRole dummy = new SimpleRole("dummy",coinContract);
        dummy.addRequiredReference("expected_counter_issuer", Role.RequiredMode.ALL_OF);
        coinContract.addRole(dummy);

        SimpleRole expectedCounterIssuerRoleCoin = new SimpleRole("expected_counter_issuer",coinContract);
        expectedCounterIssuerRoleCoin.addRequiredReference("ref_issue_counter",Role.RequiredMode.ALL_OF);
        coinContract.addRole(expectedCounterIssuerRoleCoin);

        Reference expectedCounterIssuerRefCoin = new Reference(coinContract);
        expectedCounterIssuerRefCoin.name = "expected_counter_issuer";
        expectedCounterIssuerRefCoin.setConditions(Binder.of("all_of",Do.listOf(
                "ref.origin==this.definition.data.multisig_origin",
                "this.id in ref.newItems",
                "this can_perform ref.state.roles.issue_counter"
        )));
        coinContract.addReference(expectedCounterIssuerRefCoin);

        coinContract.getStateData().put("amount","1000");
        coinContract.getDefinition().getData().put("multisig_origin",multisigContract.getOrigin());
        coinContract.getDefinition().getData().put("coin_code","uBTC");


        coinContract.seal();


        counterContract.addNewItems(coinContract);
        counterContract.seal();
        counterContract.addSignatureToSeal(key1);
        counterContract.addSignatureToSeal(key2);
        counterContract.getTransactionPack().addReferencedItem(multisigContract);
        counterContract.check();
        counterContract.traceErrors();
        assertEquals(counterContract.getErrors().size(),0);

    }


}
