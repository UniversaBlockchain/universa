package com.icodici.universa.node2;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class NameCacheTest {

    @Test
    public void busyTest() throws Exception {
        NameCache nameCache = new NameCache(Duration.ofMillis(10));
        String testName = "testName";
        assertEquals(true, nameCache.lockName(testName));
        assertEquals(false, nameCache.lockName(testName));
        Thread.sleep(15);
        nameCache.cleanUp();
        assertEquals(true, nameCache.lockName(testName));
        nameCache.unlockName(testName);
        assertEquals(true, nameCache.lockName(testName));
    }

}
