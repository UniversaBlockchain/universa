package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NameCacheTest {

    @Test
    public void busyTest() throws Exception {
        NameCache nameCache = new NameCache(Duration.ofMillis(10));
        String testName = "testName";
        assertEquals(true, nameCache.lockNameList(Arrays.asList(testName)).isEmpty());
        assertEquals(false, nameCache.lockNameList(Arrays.asList(testName)).isEmpty());
        Thread.sleep(15);
        nameCache.cleanUp();
        assertEquals(true, nameCache.lockNameList(Arrays.asList(testName)).isEmpty());
        nameCache.unlockNameList(Arrays.asList(testName));
        assertEquals(true, nameCache.lockNameList(Arrays.asList(testName)).isEmpty());
    }


    @Test
    public void multiLock() throws Exception {
        NameCache nameCache = new NameCache(Duration.ofMillis(10));
        List<HashId> values1hashes = Arrays.asList(HashId.createRandom(), HashId.createRandom(), HashId.createRandom());
        List<HashId> values2hashes = Arrays.asList(HashId.createRandom(), HashId.createRandom());
        List<HashId> valuesExhashes = new ArrayList<>();
        valuesExhashes.addAll(values1hashes);
        valuesExhashes.addAll(values2hashes);
        List<String> values1 = new ArrayList<>();
        List<String> values2 = new ArrayList<>();
        values1hashes.forEach((hash) -> values1.add(hash.toBase64String()));
        values2hashes.forEach((hash) -> values2.add(hash.toBase64String()));
        List<String> valuesEx = new ArrayList<>();
        valuesEx.addAll(values1);
        valuesEx.addAll(values2);

        assertTrue(nameCache.lockNameList(values1).isEmpty());
        assertFalse(nameCache.lockNameList(valuesEx).isEmpty());
        assertTrue(nameCache.lockNameList(values2).isEmpty());

        assertTrue(nameCache.lockOriginList(values1hashes).isEmpty());
        assertFalse(nameCache.lockOriginList(valuesExhashes).isEmpty());
        assertTrue(nameCache.lockOriginList(values2hashes).isEmpty());

        assertTrue(nameCache.lockAddressList(values1).isEmpty());
        assertFalse(nameCache.lockAddressList(valuesEx).isEmpty());
        assertTrue(nameCache.lockAddressList(values2).isEmpty());

        nameCache.unlockNameList(values1);
        nameCache.unlockOriginList(values1hashes);
        nameCache.unlockAddressList(values1);

        assertFalse(nameCache.lockNameList(valuesEx).isEmpty());
        assertFalse(nameCache.lockOriginList(valuesExhashes).isEmpty());
        assertFalse(nameCache.lockAddressList(valuesEx).isEmpty());

        nameCache.unlockNameList(values2);
        nameCache.unlockOriginList(values2hashes);
        nameCache.unlockAddressList(values2);

        assertTrue(nameCache.lockNameList(valuesEx).isEmpty());
        assertTrue(nameCache.lockOriginList(valuesExhashes).isEmpty());
        assertTrue(nameCache.lockAddressList(valuesEx).isEmpty());

        nameCache.cleanUp();

        assertFalse(nameCache.lockNameList(valuesEx).isEmpty());
        assertFalse(nameCache.lockOriginList(valuesExhashes).isEmpty());
        assertFalse(nameCache.lockAddressList(valuesEx).isEmpty());

        Thread.sleep(15);
        nameCache.cleanUp();

        assertTrue(nameCache.lockNameList(valuesEx).isEmpty());
        assertTrue(nameCache.lockOriginList(valuesExhashes).isEmpty());
        assertTrue(nameCache.lockAddressList(valuesEx).isEmpty());
    }

}
