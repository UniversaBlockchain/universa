package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.TestCase;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import net.sergeych.utils.Base64u;
import org.junit.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;

public class BrokenConnectivityMainTest extends BaseMainTest {

    @Test
    public void basicTest() throws Exception {

        //no connection between 1 and 3 and 1 and 4
        List<Main> mains = new ArrayList<>();
        PrivateKey clientKey = TestKeys.privateKey(1);
        for(int i =1; i <=4 ; i++) {
            Main m = createMain("node" + i, "_broken_connectivity_1", false);
            mains.add(m);
            m.config.getAddressesWhiteList().add(clientKey.getPublicKey().getLongAddress());
        }


        List topology = JsonTool.fromJson(new String(Do.read("./src/test_node_config_v2_broken_connectivity_1/test_node_config_v2.json")));

        Map<Integer,Client> clients = new HashMap<>();

        for(int i = 0; i < topology.size();i++) {
            Map info = ((Map) topology.get(i));
            Binder b = Binder.convertAllMapsToBinders(info);

            String url = (String) b.getListOrThrow("direct_urls").get(0);
            PublicKey key = new PublicKey(Base64u.decodeCompactString(b.getStringOrThrow("key")));
            Integer number = b.getIntOrThrow("number");
            Client c = new Client(url, clientKey, key, null);
            clients.put(number,c);
        }


        Thread.sleep(10000);


        for(int i = 0; i < clients.size();i++) {
            for(int j = 0; j < clients.size();j++) {
                Binder res = clients.get(i+1).pingNode(j+1, 200);
                boolean noConnection = i == 0 && j > 1 || j == 0 && i > 1;
                if(noConnection) {
                    assertEquals(res.getIntOrThrow("UDP"),-1);
                    assertEquals(res.getIntOrThrow("TCP"),-1);
                } else {
                    assertTrue(res.getIntOrThrow("UDP")>=0);
                    assertTrue(res.getIntOrThrow("TCP")>=0);
                }
            }
        }
        //start registration on node #2

        Contract contract = new Contract(clientKey);
        contract.seal();
        assertEquals(clients.get(2).register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);

        assertEquals(clients.get(3).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(4).getState(contract.getId()).state,ItemState.APPROVED);
        ItemResult ir;
        do {
            Thread.sleep(500);
            ir = clients.get(1).getState(contract.getId());
            System.out.println(ir);
        } while (ir.state == ItemState.UNDEFINED || ir.state.isPending());



        //start registration on node #1

        contract = new Contract(clientKey);
        contract.seal();
        assertEquals(clients.get(1).register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);

        assertEquals(clients.get(2).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(3).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(4).getState(contract.getId()).state,ItemState.APPROVED);


        //start registration on node #3
        contract = new Contract(clientKey);
        contract.seal();
        assertEquals(clients.get(3).register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);

        assertEquals(clients.get(2).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(4).getState(contract.getId()).state,ItemState.APPROVED);
        do {
            Thread.sleep(500);
            ir = clients.get(1).getState(contract.getId());
            System.out.println(ir);
        } while (ir.state == ItemState.UNDEFINED || ir.state.isPending());


        mains.forEach(m->m.shutdown());
    }



    @Test
    public void lineTest() throws Exception {

        //no connection between 1 and 3 and 1 and 4
        List<Main> mains = new ArrayList<>();
        PrivateKey clientKey = TestKeys.privateKey(1);
        for(int i =1; i <=4 ; i++) {
            Main m = createMain("node" + i, "_broken_connectivity_2", false);
            mains.add(m);
            m.config.getAddressesWhiteList().add(clientKey.getPublicKey().getLongAddress());
        }


        List topology = JsonTool.fromJson(new String(Do.read("./src/test_node_config_v2_broken_connectivity_2/test_node_config_v2.json")));

        Map<Integer,Client> clients = new HashMap<>();

        for(int i = 0; i < topology.size();i++) {
            Map info = ((Map) topology.get(i));
            Binder b = Binder.convertAllMapsToBinders(info);

            String url = (String) b.getListOrThrow("direct_urls").get(0);
            PublicKey key = new PublicKey(Base64u.decodeCompactString(b.getStringOrThrow("key")));
            Integer number = b.getIntOrThrow("number");
            Client c = new Client(url, clientKey, key, null);
            clients.put(number,c);
        }


        Thread.sleep(10000);


        for(int i = 0; i < clients.size();i++) {
            for(int j = 0; j < clients.size();j++) {
                Binder res = clients.get(i+1).pingNode(j+1, 200);
                boolean noConnection = Math.abs(i-j) > 1;
                if(noConnection) {
                    assertEquals(res.getIntOrThrow("UDP"),-1);
                    assertEquals(res.getIntOrThrow("TCP"),-1);
                } else {
                    assertTrue(res.getIntOrThrow("UDP")>=0);
                    assertTrue(res.getIntOrThrow("TCP")>=0);
                }
            }
        }
        //start registration on node #2

        Contract contract = new Contract(clientKey);
        contract.seal();
        assertEquals(clients.get(2).register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);

        assertEquals(clients.get(3).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(4).getState(contract.getId()).state,ItemState.APPROVED);
        ItemResult ir;
        do {
            Thread.sleep(500);
            ir = clients.get(1).getState(contract.getId());
            System.out.println(ir);
        } while (ir.state == ItemState.UNDEFINED || ir.state.isPending());



        //start registration on node #1

        contract = new Contract(clientKey);
        contract.seal();
        assertEquals(clients.get(1).register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);

        assertEquals(clients.get(2).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(3).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(4).getState(contract.getId()).state,ItemState.APPROVED);


        //start registration on node #3
        contract = new Contract(clientKey);
        contract.seal();
        assertEquals(clients.get(3).register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);

        assertEquals(clients.get(2).getState(contract.getId()).state,ItemState.APPROVED);
        assertEquals(clients.get(4).getState(contract.getId()).state,ItemState.APPROVED);
        do {
            Thread.sleep(500);
            ir = clients.get(1).getState(contract.getId());
            System.out.println(ir);
        } while (ir.state == ItemState.UNDEFINED || ir.state.isPending());


        mains.forEach(m->m.shutdown());
    }


}
