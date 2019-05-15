package com.icodici.universa.node2;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Core;
import com.icodici.universa.TestCase;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientHTTPServer;
import com.icodici.universa.node2.network.TopologyBuilder;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TopologyTest {

    NetConfig config1;
    ArrayList<ClientHTTPServer> servers;

    @Before
    public void init() throws IOException {
        config1 = new NetConfig();
        for(int i = 0; i < 30; i ++) {
            config1.addNode(new NodeInfo(TestKeys.publicKey(i),i+1,"node"+i,"127.0.0.1",null,"localhost",27000+i,8000+i,9000+i));
        }

        servers = new ArrayList<>();
        Config cfg = new Config();
        for(int i = 0; i < 30; i++) {

            servers.add(new ClientHTTPServer(TestKeys.privateKey(i),config1.getInfo(i+1).getClientAddress().getPort(),null));
            servers.get(i).setConfig(cfg);
            servers.get(i).setNetConfig(config1);
        }
    }

    @After
    public void finish() {
        //servers.forEach(s->{try{s.shutdown();} catch (Exception ignored){}});
    }

    @Ignore
    @Test
    public void simpleTest() throws Exception {

        TopologyBuilder tb = new TopologyBuilder("./src/test_contracts/topology/test.json", System.getProperty("java.io.tmpdir"));
    }
}
