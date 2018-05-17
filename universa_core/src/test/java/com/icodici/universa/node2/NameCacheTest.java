package com.icodici.universa.node2;

import org.junit.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameCacheTest {

    @Test
    public void busyTest() throws Exception {
        NameCache nameCache = new NameCache(Duration.ofMillis(10));
        String testName = "testName";
        assertEquals(true, nameCache.lockNameList(Arrays.asList(testName)));
        assertEquals(false, nameCache.lockNameList(Arrays.asList(testName)));
        Thread.sleep(15);
        nameCache.cleanUp();
        assertEquals(true, nameCache.lockNameList(Arrays.asList(testName)));
        nameCache.unlockNameList(Arrays.asList(testName));
        assertEquals(true, nameCache.lockNameList(Arrays.asList(testName)));
    }


    @Test
    public void multiLock() throws Exception {
        NameCache nameCache = new NameCache(Duration.ofMillis(10));
        List<String> values1 = Arrays.asList("val1", "val2", "val3");
        List<String> values2 = Arrays.asList("val4", "val5");
        List<String> valuesEx = Arrays.asList("val1", "val2", "val3", "val4", "val5");

        assertTrue(nameCache.lockNameList(values1));
        assertFalse(nameCache.lockNameList(valuesEx));
        assertTrue(nameCache.lockNameList(values2));

        assertTrue(nameCache.lockOriginList(values1));
        assertFalse(nameCache.lockOriginList(valuesEx));
        assertTrue(nameCache.lockOriginList(values2));

        assertTrue(nameCache.lockAddressList(values1));
        assertFalse(nameCache.lockAddressList(valuesEx));
        assertTrue(nameCache.lockAddressList(values2));

        nameCache.unlockNameList(values1);
        nameCache.unlockOriginList(values1);
        nameCache.unlockAddressList(values1);

        assertFalse(nameCache.lockNameList(valuesEx));
        assertFalse(nameCache.lockOriginList(valuesEx));
        assertFalse(nameCache.lockAddressList(valuesEx));

        nameCache.unlockNameList(values2);
        nameCache.unlockOriginList(values2);
        nameCache.unlockAddressList(values2);

        assertTrue(nameCache.lockNameList(valuesEx));
        assertTrue(nameCache.lockOriginList(valuesEx));
        assertTrue(nameCache.lockAddressList(valuesEx));

        nameCache.cleanUp();

        assertFalse(nameCache.lockNameList(valuesEx));
        assertFalse(nameCache.lockOriginList(valuesEx));
        assertFalse(nameCache.lockAddressList(valuesEx));

        Thread.sleep(15);
        nameCache.cleanUp();

        assertTrue(nameCache.lockNameList(valuesEx));
        assertTrue(nameCache.lockOriginList(valuesEx));
        assertTrue(nameCache.lockAddressList(valuesEx));
    }

}
