package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.TestCase;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.InnerContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.FollowerCallback;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.icodici.universa.TestCase.assertAlmostSame;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

@Ignore
public class FollowerMainTest extends BaseMainTest {
    @Test
    public void synchronizeFollowerCallbackCompleted() throws Exception {

        PrivateKey issuerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> followerIssuerPrivateKeys = new HashSet<>();
        followerIssuerPrivateKeys.add(key);
        Set<PublicKey> followerIssuerPublicKeys = new HashSet<>();
        followerIssuerPublicKeys.add(key.getPublicKey());

        Set<PublicKey> simpleIssuerPublicKeys = new HashSet<>();
        simpleIssuerPublicKeys.add(key.getPublicKey());

        SimpleRole ownerRole = new SimpleRole("owner", simpleIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        // configuration for test
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(3));
            n.config.setFollowerCallbackExpiration(Duration.ofSeconds(20));
            //n.node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE)
        });

        // contract for follow
        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        testSpace.client.register(simpleContract.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContract.getId()).state, ItemState.APPROVED);

        // callback key
        PrivateKey callbackKey = new PrivateKey(2048);

        // follower contract
        FollowerContract followerContract = ContractsService.createFollowerContract(followerIssuerPrivateKeys,
                followerIssuerPublicKeys, testSpace.client.getConfigProvider());
        followerContract.putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7783/follow.callback",
                callbackKey.getPublicKey());

        // payment contract
        Contract payment = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        ItemResult itemResult = testSpace.client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel payingParcel = ContractsService.createPayingParcel(followerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        followerContract.check();
        followerContract.traceErrors();
        assertTrue(followerContract.isOk());

        // register follower contract
        ZonedDateTime timeReg = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()),
                ZoneId.systemDefault());
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(followerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(followerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        // check before callback
        double callbackRate = testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name() + ":callback").doubleValue();
        double days = 200.0 * testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name()).doubleValue();
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg.plusSeconds(seconds);

        // for muted calculate
        seconds = (long) (callbackRate * 24 * 3600);

        Set<Long> envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires);
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires);
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: 0.0");
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(0, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        Thread.sleep(5000);

        // additional check for all network nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires);
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: 0.0");
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(0, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // create revision of follow contract (with callback) in non full network
        Contract simpleContractRevision = simpleContract.createRevision(key);
        simpleContractRevision.setOwnerKeys(key);
        simpleContractRevision.seal();
        simpleContractRevision.check();
        simpleContractRevision.traceErrors();
        assertTrue(simpleContractRevision.isOk());

        testSpace.client.register(simpleContractRevision.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // shutdown one of nodes
        int absentNode = testSpace.nodes.size() - 1;
        int absentNodeNumber = testSpace.nodes.get(absentNode).node.getNumber();
        testSpace.nodes.get(absentNode).shutdown();
        testSpace.nodes.remove(absentNode);

        // init follower callback
        FollowerCallback callback = new FollowerCallback(callbackKey, 7783, "/follow.callback");

        Thread.sleep(10000);

        // check callbacks completed in non full network
        envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                assertTrue(testSpace.node.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: " + callbackRate * 2);
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        // additional check for all network nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: " + callbackRate * 2);
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n -> n.shutdown());
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // check callbacks completed in full network (except absent node)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        if (absentNodeNumber == networkNode.node.getNumber()) {
                            System.out.println("expected: " + calculateExpires);
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                        } else {
                            System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                        }
                    }

                    FollowerService fs = environment.getFollowerService();
                    if (absentNodeNumber == networkNode.node.getNumber()) {
                        System.out.println("expected expiresAt: " + calculateExpires);
                        System.out.println("found expiresAt: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                        //started on absent node, but not completed
                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected started callbacks: 2");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(2, fs.getStartedCallbacks());

                        System.out.println("expected callbacks spent: 0.0");
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(0, fs.getCallbacksSpent(), 0.001);
                    } else {
                        System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found expiresAt: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected started callbacks: 0");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(0, fs.getStartedCallbacks());

                        System.out.println("expected callbacks spent: " + callbackRate * 2);
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
                    }
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        System.out.println("wait for starting callback expired...");
        Thread.sleep(18000 + 3000 * testSpace.nodes.size());

        // check expired callbacks (on absent node)
        for (Main networkNode : testSpace.nodes) {
            if (absentNodeNumber == networkNode.node.getNumber()) {
                envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
                if (envs.size() > 0) {
                    for (Long envId : envs) {
                        Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                        assertEquals(callbacks.size(), 2);
                        Iterator<CallbackRecord> it = callbacks.iterator();
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                    }
                } else {
                    fail("FollowerSubscription was not found");
                }
            }
        }

        System.out.println("wait for synchronization after network nodes starting...");
        Thread.sleep(40000);

        // check callbacks completed in full network
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: " + callbackRate * 2);
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // return configuration
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(10));
            n.config.setFollowerCallbackExpiration(Duration.ofMinutes(10));
        });

        callback.shutdown();

        testSpace.nodes.forEach(x -> x.shutdown());
    }

    @Test
    public void synchronizeFollowerCallbackFailed() throws Exception {

        PrivateKey issuerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> followerIssuerPrivateKeys = new HashSet<>();
        followerIssuerPrivateKeys.add(key);
        Set<PublicKey> followerIssuerPublicKeys = new HashSet<>();
        followerIssuerPublicKeys.add(key.getPublicKey());

        Set<PublicKey> simpleIssuerPublicKeys = new HashSet<>();
        simpleIssuerPublicKeys.add(key.getPublicKey());

        SimpleRole ownerRole = new SimpleRole("owner", simpleIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        // configuration for test
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(3));
            n.config.setFollowerCallbackExpiration(Duration.ofSeconds(10));
            //n.node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE)
        });

        // contract for follow
        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        testSpace.client.register(simpleContract.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContract.getId()).state, ItemState.APPROVED);

        // callback key
        PrivateKey callbackKey = new PrivateKey(2048);

        // follower contract
        FollowerContract followerContract = ContractsService.createFollowerContract(followerIssuerPrivateKeys,
                followerIssuerPublicKeys, testSpace.client.getConfigProvider());
        followerContract.putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7784/follow.callback",
                callbackKey.getPublicKey());

        // payment contract
        Contract payment = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        ItemResult itemResult = testSpace.client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel payingParcel = ContractsService.createPayingParcel(followerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        followerContract.check();
        followerContract.traceErrors();
        assertTrue(followerContract.isOk());

        // register follower contract
        ZonedDateTime timeReg = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()),
                ZoneId.systemDefault());
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(followerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(followerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        // save payment
        payment = followerContract.getNew().get(0);

        // check before callback
        double callbackRate = testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name() + ":callback").doubleValue();
        double days = 200.0 * testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name()).doubleValue();
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg.plusSeconds(seconds);

        // for muted calculate
        seconds = (long) (callbackRate * 24 * 3600);

        Set<Long> envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires);
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires);
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: 0.0");
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(0, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        Thread.sleep(5000);

        // additional check for all network nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires);
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: 0.0");
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(0, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // create revision of follow contract (with callback) in non full network
        Contract simpleContractRevision = simpleContract.createRevision(key);
        simpleContractRevision.setOwnerKeys(key);
        simpleContractRevision.seal();
        simpleContractRevision.check();
        simpleContractRevision.traceErrors();
        assertTrue(simpleContractRevision.isOk());

        testSpace.client.register(simpleContractRevision.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // shutdown one of nodes
        int absentNode = testSpace.nodes.size() - 1;
        int absentNodeNumber = testSpace.nodes.get(absentNode).node.getNumber();
        testSpace.nodes.get(absentNode).shutdown();
        testSpace.nodes.remove(absentNode);

        System.out.println("wait for callback expired...");
        Thread.sleep(16000 + 6000 * testSpace.nodes.size());

        // check callbacks expired in non full network
        envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                Collection<CallbackRecord> callbacks = testSpace.node.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                assertEquals(callbacks.size(), 2);
                Iterator<CallbackRecord> it = callbacks.iterator();
                assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.EXPIRED);
                assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.EXPIRED);

                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires);
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires);
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: 0.0");
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(0, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        // additional check for all network nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                    assertEquals(callbacks.size(), 2);
                    Iterator<CallbackRecord> it = callbacks.iterator();
                    assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.EXPIRED);
                    assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.EXPIRED);

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires);
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: 0.0");
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(0, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n -> n.shutdown());
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // check callbacks expired in full network (except absent node)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                    assertEquals(callbacks.size(), 2);
                    Iterator<CallbackRecord> it = callbacks.iterator();

                    if (absentNodeNumber == networkNode.node.getNumber()) {
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                    } else {
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.EXPIRED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.EXPIRED);
                    }

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        if (absentNodeNumber == networkNode.node.getNumber()) {
                            System.out.println("expected: " + calculateExpires);
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                        } else {
                            System.out.println("expected: " + calculateExpires);
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                        }
                    }

                    FollowerService fs = environment.getFollowerService();
                    if (absentNodeNumber == networkNode.node.getNumber()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                        //started on absent node, but not completed
                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected callbacks spent: 0.0");
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(0, fs.getCallbacksSpent(), 0.001);

                        System.out.println("expected started callbacks: 2");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(2, fs.getStartedCallbacks());
                    } else {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                        System.out.println("expected callbacks spent: 0.0");
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(0, fs.getCallbacksSpent(), 0.001);

                        System.out.println("expected started callbacks: 0");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(0, fs.getStartedCallbacks());
                    }
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // refilling follower contract to start synchronization of callbacks
        FollowerContract newRevFollowerContract = (FollowerContract) followerContract.createRevision(key);
        newRevFollowerContract.attachToNetwork(testSpace.client);
        newRevFollowerContract.seal();

        payingParcel = ContractsService.createPayingParcel(newRevFollowerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        newRevFollowerContract.check();
        newRevFollowerContract.traceErrors();
        assertTrue(newRevFollowerContract.isOk());

        // register revision of follower contract
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(newRevFollowerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(newRevFollowerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        // check subscriptions after refilling
        days = 400.0 * testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name()).doubleValue();
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg.plusSeconds(seconds);

        // for muted calculate
        seconds = (long) (callbackRate * 24 * 3600);

        System.out.println("wait for synchronization after refilling follower contract...");
        Thread.sleep(10000);

        // check callbacks failed in full network
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires);
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: 0.0");
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(0, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // return configuration
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(10));
            n.config.setFollowerCallbackExpiration(Duration.ofMinutes(10));
        });

        testSpace.nodes.forEach(x -> x.shutdown());
    }

    @Test
    public void synchronizeFollowerCallbackImpossible() throws Exception {

        PrivateKey issuerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> followerIssuerPrivateKeys = new HashSet<>();
        followerIssuerPrivateKeys.add(key);
        Set<PublicKey> followerIssuerPublicKeys = new HashSet<>();
        followerIssuerPublicKeys.add(key.getPublicKey());

        Set<PublicKey> simpleIssuerPublicKeys = new HashSet<>();
        simpleIssuerPublicKeys.add(key.getPublicKey());

        SimpleRole ownerRole = new SimpleRole("owner", simpleIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        // configuration for test
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(3));
            n.config.setFollowerCallbackExpiration(Duration.ofSeconds(20));
            //n.node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE)
        });

        // contract for follow
        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        testSpace.client.register(simpleContract.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContract.getId()).state, ItemState.APPROVED);

        // callback key
        PrivateKey callbackKey = new PrivateKey(2048);

        // follower contract
        FollowerContract followerContract = ContractsService.createFollowerContract(followerIssuerPrivateKeys,
                followerIssuerPublicKeys, testSpace.client.getConfigProvider());
        followerContract.putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7785/follow.callback",
                callbackKey.getPublicKey());

        // payment contract
        Contract payment = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        ItemResult itemResult = testSpace.client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel payingParcel = ContractsService.createPayingParcel(followerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        followerContract.check();
        followerContract.traceErrors();
        assertTrue(followerContract.isOk());

        // register follower contract
        ZonedDateTime timeReg = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()),
                ZoneId.systemDefault());
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(followerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(followerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        // save payment
        payment = followerContract.getNew().get(0);

        // check before callback
        double callbackRate = testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name() + ":callback").doubleValue();
        double days = 200.0 * testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name()).doubleValue();
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg.plusSeconds(seconds);

        // for muted calculate
        seconds = (long) (callbackRate * 24 * 3600);

        Set<Long> envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires);
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires);
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: 0.0");
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(0, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        Thread.sleep(5000);

        // additional check for all network nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires);
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: 0.0");
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(0, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // create revision of follow contract (with callbacks) in non full network
        Contract simpleContractRevision = simpleContract.createRevision(key);
        simpleContractRevision.setOwnerKeys(key);
        simpleContractRevision.seal();
        simpleContractRevision.check();
        simpleContractRevision.traceErrors();
        assertTrue(simpleContractRevision.isOk());

        testSpace.client.register(simpleContractRevision.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // shutdown all nodes except 0 (synchronization is impossible!)
        Set<Integer> absentNodeNumbers = new HashSet();
        for (int i = testSpace.nodes.size() - 1; i > 0; i--) {
            absentNodeNumbers.add(testSpace.nodes.get(1).node.getNumber());
            testSpace.nodes.get(1).shutdown();
            testSpace.nodes.remove(1);
        }

        // init follower callback
        FollowerCallback callback = new FollowerCallback(callbackKey, 7785, "/follow.callback");

        Thread.sleep(3000 * (absentNodeNumbers.size() + 3));

        // check callbacks completed on 0 node
        envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                assertTrue(testSpace.node.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: " + callbackRate * 2);
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        // recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n -> n.shutdown());
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // check callbacks completed in full network (except absent nodes)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                            System.out.println("expected: " + calculateExpires);
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                        } else {
                            System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                        }
                    }

                    FollowerService fs = environment.getFollowerService();
                    if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                        //started on absent node, but not completed
                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected callbacks spent: 0.0");
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(0, fs.getCallbacksSpent(), 0.001);

                        System.out.println("expected started callbacks: 2");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(2, fs.getStartedCallbacks());
                    } else {
                        System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected callbacks spent: " + callbackRate * 2);
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);

                        System.out.println("expected started callbacks: 0");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(0, fs.getStartedCallbacks());
                    }
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        System.out.println("wait for starting callback expired...");
        Thread.sleep(18000 + 3000 * testSpace.nodes.size());

        // check expired callbacks (on absent nodes)
        for (Main networkNode : testSpace.nodes) {
            if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
                if (envs.size() > 0) {
                    for (Long envId : envs) {
                        Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                        assertEquals(callbacks.size(), 2);
                        Iterator<CallbackRecord> it = callbacks.iterator();
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                    }
                } else {
                    fail("FollowerSubscription was not found");
                }
            }
        }

        // refilling follower contract to start synchronization of callbacks
        FollowerContract newRevFollowerContract = (FollowerContract) followerContract.createRevision(key);
        newRevFollowerContract.attachToNetwork(testSpace.client);
        newRevFollowerContract.seal();

        payingParcel = ContractsService.createPayingParcel(newRevFollowerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        newRevFollowerContract.check();
        newRevFollowerContract.traceErrors();
        assertTrue(newRevFollowerContract.isOk());

        // register revision of follower contract
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(newRevFollowerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(newRevFollowerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        System.out.println("wait for synchronization after refilling follower contract...");
        Thread.sleep(25000);

        // check callback records remove after synchronization is impossible
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs)
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // return configuration
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(10));
            n.config.setFollowerCallbackExpiration(Duration.ofMinutes(10));
        });

        callback.shutdown();

        testSpace.nodes.forEach(x -> x.shutdown());
    }

    @Test
    public void synchronizeFollowerCallbackWithConsensusAtSecondAttempt() throws Exception {

        PrivateKey issuerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/reconfig_key.private.unikey"));
        TestSpace testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> followerIssuerPrivateKeys = new HashSet<>();
        followerIssuerPrivateKeys.add(key);
        Set<PublicKey> followerIssuerPublicKeys = new HashSet<>();
        followerIssuerPublicKeys.add(key.getPublicKey());

        Set<PublicKey> simpleIssuerPublicKeys = new HashSet<>();
        simpleIssuerPublicKeys.add(key.getPublicKey());

        SimpleRole ownerRole = new SimpleRole("owner", simpleIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        // configuration for test
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(3));
            n.config.setFollowerCallbackExpiration(Duration.ofSeconds(20));
            //n.node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE)
        });

        // contract for follow
        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        testSpace.client.register(simpleContract.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContract.getId()).state, ItemState.APPROVED);

        // callback key
        PrivateKey callbackKey = new PrivateKey(2048);

        // follower contract
        FollowerContract followerContract = ContractsService.createFollowerContract(followerIssuerPrivateKeys,
                followerIssuerPublicKeys, testSpace.client.getConfigProvider());
        followerContract.putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7786/follow.callback",
                callbackKey.getPublicKey());

        // payment contract
        Contract payment = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        ItemResult itemResult = testSpace.client.register(payment.getPackedTransaction(), 5000);
        System.out.println("payment: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel payingParcel = ContractsService.createPayingParcel(followerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        followerContract.check();
        followerContract.traceErrors();
        assertTrue(followerContract.isOk());

        // register follower contract
        ZonedDateTime timeReg = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()),
                ZoneId.systemDefault());
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(followerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(followerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        // save payment
        payment = followerContract.getNew().get(0);

        // check before callback
        double callbackRate = testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name() + ":callback").doubleValue();
        double days = 200.0 * testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name()).doubleValue();
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg.plusSeconds(seconds);

        // for muted calculate
        seconds = (long) (callbackRate * 24 * 3600);

        Set<Long> envs = testSpace.node.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
        if (envs.size() > 0) {
            for (Long envId : envs) {
                NImmutableEnvironment environment = testSpace.node.node.getLedger().getEnvironment(envId);
                for (ContractSubscription foundCss : environment.subscriptions()) {
                    System.out.println("expected: " + calculateExpires);
                    System.out.println("found: " + foundCss.expiresAt());
                    assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                }

                FollowerService fs = environment.getFollowerService();
                System.out.println("expected expiresAt: " + calculateExpires);
                System.out.println("found expiresAt: " + fs.expiresAt());
                assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                System.out.println("found mutedAt: " + fs.mutedAt());
                assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                System.out.println("expected started callbacks: 0");
                System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                assertEquals(0, fs.getStartedCallbacks());

                System.out.println("expected callbacks spent: 0.0");
                System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                assertEquals(0, fs.getCallbacksSpent(), 0.001);
            }
        } else {
            fail("FollowerSubscription was not found");
        }

        Thread.sleep(5000);

        // additional check for all network nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires);
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: 0.0");
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(0, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // create revision of follow contract (with callback) in non full network
        Contract simpleContractRevision = simpleContract.createRevision(key);
        simpleContractRevision.setOwnerKeys(key);
        simpleContractRevision.seal();
        simpleContractRevision.check();
        simpleContractRevision.traceErrors();
        assertTrue(simpleContractRevision.isOk());

        testSpace.client.register(simpleContractRevision.getPackedTransaction(), 3000);
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // shutdown two nodes
        Set<Integer> absentNodeNumbers = new HashSet();
        for (int i = testSpace.nodes.size() - 1; i > 1; i--) {
            absentNodeNumbers.add(testSpace.nodes.get(1).node.getNumber());
            testSpace.nodes.get(1).shutdown();
            testSpace.nodes.remove(1);
        }

        // init follower callback
        FollowerCallback callback = new FollowerCallback(callbackKey, 7786, "/follow.callback");

        Thread.sleep(15000);

        // check callbacks completed on 2 nodes
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found: " + foundCss.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                    }

                    FollowerService fs = environment.getFollowerService();
                    System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                    System.out.println("found expiresAt: " + fs.expiresAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                    System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                    System.out.println("found mutedAt: " + fs.mutedAt());
                    assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                    System.out.println("expected started callbacks: 0");
                    System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                    assertEquals(0, fs.getStartedCallbacks());

                    System.out.println("expected callbacks spent: " + callbackRate * 2);
                    System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                    assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n -> n.shutdown());
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // shutdown one of nodes (for unreached limit synchronization)
        int absentNode = testSpace.nodes.size() - 1;
        int absentNodeNumber = testSpace.nodes.get(absentNode).node.getNumber();
        testSpace.nodes.get(absentNode).shutdown();
        testSpace.nodes.remove(absentNode);

        // check callbacks completed in full network (except absent nodes)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                    for (ContractSubscription foundCss : environment.subscriptions()) {
                        if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                            System.out.println("expected: " + calculateExpires);
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires, foundCss.expiresAt(), 5);
                        } else {
                            System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                        }
                    }

                    FollowerService fs = environment.getFollowerService();
                    if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                        System.out.println("expected: " + calculateExpires);
                        System.out.println("found: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires, fs.expiresAt(), 5);

                        //started on absent node, but not completed
                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected callbacks spent: 0.0");
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(0, fs.getCallbacksSpent(), 0.001);

                        System.out.println("expected started callbacks: 2");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(2, fs.getStartedCallbacks());
                    } else {
                        System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected callbacks spent: " + callbackRate * 2);
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);

                        System.out.println("expected started callbacks: 0");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(0, fs.getStartedCallbacks());
                    }
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        System.out.println("wait for starting callback expired...");
        Thread.sleep(37000 + 6000 * testSpace.nodes.size());

        // check expired callbacks (on absent nodes)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                        Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                        assertEquals(callbacks.size(), 2);
                        Iterator<CallbackRecord> it = callbacks.iterator();
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                    } else
                        assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // refilling follower contract to start synchronization of callbacks
        FollowerContract newRevFollowerContract = (FollowerContract) followerContract.createRevision(key);
        newRevFollowerContract.attachToNetwork(testSpace.client);
        newRevFollowerContract.seal();

        payingParcel = ContractsService.createPayingParcel(newRevFollowerContract.getTransactionPack(), payment, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        newRevFollowerContract.check();
        newRevFollowerContract.traceErrors();
        assertTrue(newRevFollowerContract.isOk());

        // register revision of follower contract
        testSpace.client.registerParcel(payingParcel.pack());
        Thread.sleep(5000);

        // check payment and payload contracts
        itemResult = testSpace.client.getState(newRevFollowerContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        assertEquals(ItemState.APPROVED, itemResult.state);
        assertEquals(ItemState.APPROVED, testSpace.client.getState(newRevFollowerContract.getNew().get(0).getId()).state);
        assertEquals(ItemState.REVOKED, testSpace.client.getState(payingParcel.getPayment().getContract().getId()).state);

        // calculate expiration time for check subscriptions after refilling
        days = 400.0 * testSpace.node.config.getServiceRate(NSmartContract.SmartContractType.FOLLOWER1.name()).doubleValue();
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg.plusSeconds(seconds);

        // for muted calculate
        seconds = (long) (callbackRate * 24 * 3600);

        System.out.println("wait for synchronization after refilling follower contract...");
        Thread.sleep(25000);

        // check expired callbacks (on absent node)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                        Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                        assertEquals(callbacks.size(), 2);
                        Iterator<CallbackRecord> it = callbacks.iterator();
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                    } else
                        assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // recreate network and make sure contract is still APPROVED
        testSpace.nodes.forEach(n -> n.shutdown());
        Thread.sleep(2000);
        testSpace = prepareTestSpace(issuerKey);
        testSpace.nodes.forEach(n -> n.config.setIsFreeRegistrationsAllowedFromYaml(true));
        assertEquals(testSpace.client.getState(simpleContractRevision.getId()).state, ItemState.APPROVED);

        // check expired callbacks (on absent nodes)
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    if (absentNodeNumbers.contains(networkNode.node.getNumber())) {
                        Collection<CallbackRecord> callbacks = networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId);
                        assertEquals(callbacks.size(), 2);
                        Iterator<CallbackRecord> it = callbacks.iterator();
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                        assertEquals(it.next().getState(), NCallbackService.FollowerCallbackState.STARTED);
                    } else
                        assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        System.out.println("wait for synchronization after network nodes starting...");
        Thread.sleep(70000);

        // check callbacks completed (synchronized) in full network
        for (Main networkNode : testSpace.nodes) {
            envs = networkNode.node.getLedger().getSubscriptionEnviromentIds(simpleContract.getOrigin());
            if (envs.size() > 0) {
                for (Long envId : envs) {
                    assertTrue(networkNode.node.getLedger().getFollowerCallbacksToResyncByEnvId(envId).isEmpty());

                    if (absentNodeNumber != networkNode.node.getNumber()) {
                        NImmutableEnvironment environment = networkNode.node.getLedger().getEnvironment(envId);
                        for (ContractSubscription foundCss : environment.subscriptions()) {
                            System.out.println("expected: " + calculateExpires.minusSeconds(seconds * 2));
                            System.out.println("found: " + foundCss.expiresAt());
                            assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), foundCss.expiresAt(), 5);
                        }

                        FollowerService fs = environment.getFollowerService();
                        System.out.println("expected expiresAt: " + calculateExpires.minusSeconds(seconds * 2));
                        System.out.println("found expiresAt: " + fs.expiresAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 2), fs.expiresAt(), 5);

                        System.out.println("expected mutedAt: " + calculateExpires.minusSeconds(seconds * 3));
                        System.out.println("found mutedAt: " + fs.mutedAt());
                        assertAlmostSame(calculateExpires.minusSeconds(seconds * 3), fs.mutedAt(), 5);

                        System.out.println("expected started callbacks: 0");
                        System.out.println("found started callbacks: " + fs.getStartedCallbacks());
                        assertEquals(0, fs.getStartedCallbacks());

                        System.out.println("expected callbacks spent: " + callbackRate * 2);
                        System.out.println("found callbacks spent: " + fs.getCallbacksSpent());
                        assertEquals(callbackRate * 2, fs.getCallbacksSpent(), 0.001);
                    }
                }
            } else {
                fail("FollowerSubscription was not found");
            }
        }

        // return configuration
        testSpace.nodes.forEach(n -> {
            n.config.setFollowerCallbackDelay(Duration.ofSeconds(10));
            n.config.setFollowerCallbackExpiration(Duration.ofMinutes(10));
        });

        callback.shutdown();

        testSpace.shutdown();
    }

    @Test
    public void testFollowerApi() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        // register callback
        PrivateKey callbackKey = new PrivateKey(2048);

        Binder followerRate = client.followerGetRate();

        System.out.println("followerRate size: " + followerRate.size());
        System.out.println("rateOriginDays: " + followerRate.get("rateOriginDays"));
        System.out.println("rateCallback: " + followerRate.get("rateCallback"));

        assertEquals(main.config.getServiceRate("FOLLOWER1").toString(), followerRate.getString("rateOriginDays"));
        assertEquals(main.config.getServiceRate("FOLLOWER1" + ":callback").divide(main.config.getServiceRate("FOLLOWER1")).toString(), followerRate.getString("rateCallback"));

        Contract simpleContract = new Contract(TestKeys.privateKey(1));
        simpleContract.seal();
        ItemResult itemResult = client.register(simpleContract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        FollowerContract followerContract = ContractsService.createFollowerContract(new HashSet<>(asList(TestKeys.privateKey(1))), new HashSet<>(asList(TestKeys.publicKey(1))), client.getConfigProvider());
        followerContract.attachToNetwork(client);
        followerContract.putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey.getPublicKey());

        Contract stepaU = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        itemResult = client.register(stepaU.getPackedTransaction(), 5000);
        System.out.println("stepaU : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createPayingParcel(followerContract.getTransactionPack(), stepaU, 1, 200, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        Binder followerInfo = client.queryFollowerInfo(followerContract.getId());
        System.out.println("follower info is null: " + (followerInfo == null));
        assertNull(followerInfo);

        client.registerParcel(parcel.pack());
        Thread.sleep(5000);

        itemResult = client.getState(followerContract.getId());
        System.out.println("follower : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        followerInfo = client.queryFollowerInfo(followerContract.getId());

        System.out.println("follower info size: " + followerInfo.size());

        System.out.println("paid_U: " + followerInfo.getString("paid_U", ""));
        System.out.println("prepaid_OD: " + followerInfo.getString("prepaid_OD", ""));
        System.out.println("prepaid_from: " + followerInfo.getString("prepaid_from", ""));
        System.out.println("followed_origins: " + followerInfo.getString("followed_origins", ""));
        System.out.println("spent_OD: " + followerInfo.getString("spent_OD", ""));
        System.out.println("spent_OD_time: " + followerInfo.getString("spent_OD_time", ""));
        System.out.println("callback_rate: " + followerInfo.getString("callback_rate", ""));

        assertNotNull(followerInfo);
        assertEquals(followerInfo.size(), 9);

        assertEquals(followerInfo.getInt("paid_U", 0), followerContract.getStateData().get("paid_U"));
        assertTrue(followerInfo.getDouble("prepaid_OD") == followerContract.getPrepaidOriginsDays().doubleValue());
        assertEquals(followerInfo.getLong("prepaid_from", 0), followerContract.getStateData().get("prepaid_from"));
        assertEquals(followerInfo.getInt("followed_origins", 0), followerContract.getStateData().get("followed_origins"));
        assertEquals(followerInfo.getDouble("spent_OD"), followerContract.getStateData().get("spent_OD"));
        assertEquals(followerInfo.getLong("spent_OD_time", 0), followerContract.getStateData().get("spent_OD_time"));
        assertEquals(followerInfo.getDouble("callback_rate"), followerContract.getStateData().get("callback_rate"));
        assertEquals(followerInfo.getBinder("callback_keys").get("http://localhost:7777/follow.callback"), callbackKey.getPublicKey().pack());
        assertEquals(followerInfo.getBinder("tracking_origins").get(simpleContract.getOrigin().toBase64String()), "http://localhost:7777/follow.callback");

        assertEquals(followerContract.getCallbackKeys().get("http://localhost:7777/follow.callback"), callbackKey.getPublicKey());
        assertEquals(followerContract.getTrackingOrigins().get(simpleContract.getOrigin()), "http://localhost:7777/follow.callback");
        assertTrue(followerContract.isOriginTracking(simpleContract.getOrigin()));
        assertTrue(followerContract.isCallbackURLUsed("http://localhost:7777/follow.callback"));

        mm.forEach(x -> x.shutdown());

    }
    
}
