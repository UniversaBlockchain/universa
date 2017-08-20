package com.icodici.universa.node;

import com.icodici.universa.HashId;
import com.icodici.universa.node.network.*;
import com.oracle.tools.packager.Log;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class NodeStarterTest {


    // We need to fix shutting down instance - can't test after it...
    // java can;t for a process so we can't properly and timely shut it down
    @Test
    public void createNode() throws Exception {
        Thread t = new Thread(() -> {
            NodeStarter.main(new String[]{"-c", "src/test_config2", "-i", "node7", "-p", "15510"});
        });
        t.start();
        Thread.currentThread().sleep(400);
        HttpClient client = new HttpClient("test", "http://127.0.0.1:15510");
        HttpClient.Answer answer = client.request("network");
        assertTrue(answer.isOk());
//        System.out.println(answer.data);
        Binder n4 = answer.data.getBinderOrThrow("node4");
        assertEquals(2086, n4.getIntOrThrow("port"));
        assertEquals("127.0.0.1", n4.getStringOrThrow("ip"));
        NodeStarter.shutdown();
    }
}