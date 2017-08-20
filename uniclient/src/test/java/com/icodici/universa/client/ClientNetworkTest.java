package com.icodici.universa.client;

import net.sergeych.tools.Reporter;
import org.junit.Test;

import static org.junit.Assert.*;

public class ClientNetworkTest {
    @Test
    public void checkNetwork() throws Exception {
        ClientNetwork n = new ClientNetwork();
        assertEquals(10, n.size());
        int active = n.checkNetworkState(new Reporter());
        assertEquals(10, active);
    }
}