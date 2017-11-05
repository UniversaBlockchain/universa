package com.icodici.universa.client;

import net.sergeych.tools.ConsoleInterceptor;
import net.sergeych.tools.Reporter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClientNetworkTest {
    // we do moving the network addresses now
    @Test
    public void checkNetwork() throws Exception {
        ClientNetwork n = new ClientNetwork();
        assertEquals(30, n.size());
        ConsoleInterceptor.copyOut(() -> {
            int active = n.checkNetworkState(new Reporter());
            assertEquals(30, active);
        });
    }
}