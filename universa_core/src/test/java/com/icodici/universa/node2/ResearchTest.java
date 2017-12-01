package com.icodici.universa.node2;



import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Network;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class ResearchTest extends TestCase {

    @Before
    public void setUp() throws Exception {
        System.out.println("setUp()...");
    }



    @After
    public void tearDown() throws Exception {
        System.out.println("tearDown()...");
    }



    @Test
    public void test1() throws Exception {
        Config config = new Config();

        config.setPositiveConsensus(1);
        config.setNegativeConsensus(1);

        NodeInfo nodeInfo = new NodeInfo(getNodePublicKey(0),1,"node1","localhost",7101,7102,7104);
        NetConfig netConfig = new NetConfig(asList(nodeInfo));
        Network network = new TestSingleNetwork(netConfig);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        Ledger ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        Node node = new Node(config, nodeInfo, ledger, network);
        System.out.println(node.toString());
    }



    @Test
    public void quantiserTest() throws Exception {
        Quantiser quantiser = new Quantiser(10);
        quantiser.addWorkCost(Quantiser.PRICE_APPLICABLE_PERM);
        quantiser.addWorkCost(Quantiser.PRICE_CHECK_4096_SIG);
        try {
            quantiser.addWorkCost(Quantiser.PRICE_REGISTER_VERSION);
            assertFalse(true); // must throw QuantiserException
        } catch (Quantiser.QuantiserException e) {
            return;
        }
    }







    protected static final String ROOT_PATH = "./src/test_contracts/";
    protected static final String CONFIG_2_PATH = "./src/test_config_2/";

}
