package com.icodici.universa.client;

import net.sergeych.tools.ConsoleInterceptor;
import net.sergeych.tools.Do;
import net.sergeych.tools.Reporter;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ClientNetworkTest {
    // we do moving the network addresses now
    @Test
    public void checkNetwork() throws Exception {
        ClientNetwork n = null;
        for (int i = 1; i < 10; i++) {
            String nodeUrl = "http://node-" +
                    Do.randomIntInRange(1, 10) +
                    "-com.universa.io:8080";
            try {
                n = new ClientNetwork(nodeUrl, null);
            } catch (IOException e) {
                System.err.println("failed to read network from node " + i);
            }
        }
        if (n == null || n.client == null)
            throw new IOException("failed to connect to to the universa network");
        assertEquals(30, n.size());

        final ClientNetwork fn = n;

        ConsoleInterceptor.copyOut(() -> {
            int active = fn.checkNetworkState(new Reporter());
            assertEquals(30, active);
        });
    }
}