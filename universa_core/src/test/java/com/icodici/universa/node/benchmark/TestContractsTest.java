package com.icodici.universa.node.benchmark;

import com.icodici.universa.contract.Contract;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class TestContractsTest {
    @Test
    public void generate() throws Exception {

        try(TestContracts tc = new TestContracts(8)) {
            ArrayList<Contract> cc = new ArrayList<>(tc.generate(100));
            assertEquals(100, cc.size());
            cc.forEach(c->{
                assertTrue(c.isOk());
                byte[] packed = c.getLastSealedBinary();
                assertNotNull(packed);
                assertTrue(c.isPermitted("change_owner", tc.getPublicKey()));
                assertNotNull(c.getOwner());
                assertNotNull(c.getIssuer());
                assertTrue(c.getIssuer().isAllowedForKeys(new HashSet(asList(tc.getPublicKey()))));
                assertTrue(c.isPermitted("change_owner", tc.getPublicKey()));
            });
        }
    }
}