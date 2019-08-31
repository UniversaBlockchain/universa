/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.StateRecord;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.*;

public class TestCase {
    public static void assertAlmostSame(ZonedDateTime t1, ZonedDateTime t2) {
        assertAlmostSame(t1, t2, 2L);
    }
    public static void assertAlmostSame(ZonedDateTime t1, ZonedDateTime t2, long expectedDelta) {
        if (t1 == null && t2 == null)
            return;
        long delta = Math.abs(t1.toEpochSecond() - t2.toEpochSecond());
        MatcherAssert.assertThat(delta, Matchers.is(Matchers.lessThan(expectedDelta)));
    }

    protected void assertSameRecords(StateRecord r, StateRecord r1) {
        assertEquals(r.getId(), r1.getId());
        assertEquals(r.getState(), r1.getState());
        assertAlmostSame(r.getCreatedAt(), r1.getCreatedAt());
        assertEquals(r.getRecordId(), r1.getRecordId());
        assertEquals(r.getLockedByRecordId(), r1.getLockedByRecordId());
    }

    protected void assertThrows(Class<? extends Exception> exClass, Callable<?> block) {
        try {
            block.call();
            fail("Exception of class " + exClass.getName() + " was expected, but nothing was thrown");
        } catch (Throwable t) {
            if (exClass.isInstance(t))
                return;
            t.printStackTrace();
            fail("Expected exception of class " + exClass.getName() + "instead " + t.getClass().getName() + " was thrown");
        }
    }

    public static void assertErrorsContainsSubstr(Collection<ErrorRecord> errors, String substr) {
        boolean contains = false;
        for (ErrorRecord er : errors) {
            if (er.getMessage().contains(substr)) {
                contains = true;
                break;
            }
        }
        assertTrue(contains);
    }

    protected void assertThrows(Callable<?> callable) {
        assertThrows(Exception.class, callable);
    }

    static List<PrivateKey> nodeKeys = new ArrayList<>();

    protected static PrivateKey getNodeKey(int index) throws IOException {
        if (nodeKeys.size() == 0) {
            Files.list(Paths.get("src/test_config_2/tmp"))
                    .filter(Files::isRegularFile)
                    .forEach(fn -> {
                        if (!fn.toString().endsWith(".private.unikey"))
                            return;
                        PrivateKey prk = null;
                        try {
                            nodeKeys.add(PrivateKey.fromPath(fn));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
        return nodeKeys.get(index);
    }

    protected static PublicKey getNodePublicKey(int index) throws IOException {
        return getNodeKey(index).getPublicKey();
    }

    protected Contract readContract(String fileName) throws Exception {
        return readContractBase(fileName, false);
    }

    protected Contract readContract(String fileName, boolean isTransaction) throws Exception {
        return readContractBase(fileName, isTransaction);
    }

    protected Contract readContractBase(String fileName, boolean isTransaction) throws Exception {
        Contract contract = null;

        Path path = Paths.get(fileName);
        byte[] data = Files.readAllBytes(path);

        try {
            if (isTransaction) {
                contract = Contract.fromPackedTransaction(data);
            }
            else
                contract = new Contract(data);
        } catch (Exception e) {
            e.printStackTrace();
        }

        assertNotEquals(contract, null);
        return contract;
    }

}
