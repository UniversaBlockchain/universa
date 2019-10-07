package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.QuorumVoteRole;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class VotingTest extends BaseMainTest {

    @Test
    public void persistentVoting() throws Exception {

        TestSpace ts = prepareTestSpace();
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));


        Contract contract = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("issuer",contract,"this.state.data.list","90%");
        contract.addRole(quorumVoteRole);

        List<KeyAddress> addresses = new ArrayList<>();
        for(int i = 0; i < 20; i++) {
            addresses.add(TestKeys.publicKey(i).getShortAddress());
        }
        contract.getStateData().put("list",addresses);

        contract.seal();

        Map<Integer,ZonedDateTime> expires = new ConcurrentHashMap<>();


        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();
        for(int i = 0; i < client.size(); i++) {
            int finalI = i;
            Do.inParallel(() -> {
                try {
                    expires.put(client.getClient(finalI).getNodeNumber(), client.getClient(finalI).initiateVote(contract));
                    if(readyCounter.incrementAndGet() == client.size()) {
                        readyEvent.fire();
                    }
                } catch (ClientError clientError) {
                    clientError.printStackTrace();
                }
            });
        };

        readyEvent.await();

        assertEquals(expires.values().stream().filter(zdt -> zdt.isAfter(ZonedDateTime.now().plusDays(4))).count(),client.size());


        ts.shutdown();
        ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));


        AtomicInteger readyCounter2 = new AtomicInteger();
        AsyncEvent readyEvent2 = new AsyncEvent();


        for(int j = 0; j < 19; j++) {
            int finalJ = j;
            Do.inParallel(() -> {
                Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(finalJ));
                for (int i = 0; i < keyClient.size(); i++) {
                    int finalI = i;
                    Do.inParallel(() -> {
                        try {
                            assertEquals(keyClient.getClient(finalI).voteForContract(contract.getId()), expires.get(keyClient.getClient(finalI).getNodeNumber()));

                            if(readyCounter2.incrementAndGet() == 19*keyClient.size()) {
                                readyEvent2.fire();
                            }
                        } catch (ClientError clientError) {
                            clientError.printStackTrace();
                        }
                    });
                }
            });
        }
        readyEvent2.await();


        assertEquals(client.register(contract.getPackedTransaction(),100000).state, ItemState.APPROVED);

        ts.shutdown();
    }


    @Test
    public void persistentVotingFailed() throws Exception {

        TestSpace ts = prepareTestSpace();
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));

        Contract contract = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("issuer",contract,"this.state.data.list","99%");
        contract.addRole(quorumVoteRole);

        List<KeyAddress> addresses = new ArrayList<>();
        for(int i = 0; i < 20; i++) {
            addresses.add(TestKeys.publicKey(i).getShortAddress());
        }
        contract.getStateData().put("list",addresses);

        contract.seal();

        Map<Integer,ZonedDateTime> expires = new ConcurrentHashMap<>();


        AtomicInteger readyCounter = new AtomicInteger();
        AsyncEvent readyEvent = new AsyncEvent();
        for(int i = 0; i < client.size(); i++) {
            int finalI = i;
            Do.inParallel(() -> {
                try {
                    expires.put(client.getClient(finalI).getNodeNumber(), client.getClient(finalI).initiateVote(contract));
                    if(readyCounter.incrementAndGet() == client.size()) {
                        readyEvent.fire();
                    }
                } catch (ClientError clientError) {
                    clientError.printStackTrace();
                }
            });
        };

        readyEvent.await();

        assertEquals(expires.values().stream().filter(zdt -> zdt.isAfter(ZonedDateTime.now().plusDays(4))).count(),client.size());


        ts.shutdown();
        ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));


        AtomicInteger readyCounter2 = new AtomicInteger();
        AsyncEvent readyEvent2 = new AsyncEvent();


        for(int j = 0; j < 19; j++) {
            int finalJ = j;
            Do.inParallel(() -> {
                Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(finalJ));
                for (int i = 0; i < keyClient.size(); i++) {
                    int finalI = i;
                    Do.inParallel(() -> {
                        try {
                            assertEquals(keyClient.getClient(finalI).voteForContract(contract.getId()), expires.get(keyClient.getClient(finalI).getNodeNumber()));

                            if(readyCounter2.incrementAndGet() == 19*keyClient.size()) {
                                readyEvent2.fire();
                            }
                        } catch (ClientError clientError) {
                            clientError.printStackTrace();
                        }
                    });
                }
            });
        }
        readyEvent2.await();


        ItemResult ir = client.register(contract.getPackedTransaction(), 100000);
        System.out.println(ir);
        assertEquals(ir.state, ItemState.DECLINED);

        ts.shutdown();
    }

    @Test
    public void temporaryVoting() throws Exception {
        TestSpace ts = prepareTestSpace();
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));

        Contract contract = new Contract(TestKeys.privateKey(1));
        ListRole issuer = new ListRole("issuer",contract);
        issuer.setMode(ListRole.Mode.ALL);
        for(int i = 0; i < 10; i++) {
            issuer.addRole(new SimpleRole("@"+i,contract,Do.listOf(TestKeys.publicKey(i).getShortAddress())));
        }

        contract.addRole(issuer);
        contract.seal();

        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));


        AtomicInteger readyCounter2 = new AtomicInteger();
        AsyncEvent readyEvent2 = new AsyncEvent();


        for(int j = 0; j < 10; j++) {
            int finalJ = j;
            Do.inParallel(() -> {
                Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(finalJ));
                for (int i = 0; i < keyClient.size(); i++) {
                    int finalI = i;
                    Do.inParallel(() -> {
                        try {
                            assertTrue(keyClient.getClient(finalI).voteForContract(contract.getId()).isBefore(ZonedDateTime.now().plusHours(2)));

                            if(readyCounter2.incrementAndGet() == 10*keyClient.size()) {
                                readyEvent2.fire();
                            }
                        } catch (ClientError clientError) {
                            clientError.printStackTrace();
                        }
                    });
                }
            });
        }
        readyEvent2.await();

        ItemResult ir = client.register(contract.getPackedTransaction(), 100000);
        System.out.println(ir);
        assertEquals(ir.state, ItemState.APPROVED);

        ts.shutdown();

    }
}
