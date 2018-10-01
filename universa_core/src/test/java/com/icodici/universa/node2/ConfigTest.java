package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.node.TestCase;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class ConfigTest extends TestCase {

    @Test
    public void copyTest() {
        Config config = new Config();
        PrivateKey randomKey = new PrivateKey(4096);

        config.getKeysWhiteList().add(randomKey.getPublicKey());
        config.getAddressesWhiteList().add(new KeyAddress(randomKey.getPublicKey(), 0, true));

        Config copyConfig = config.copy();

        assertEquals(config.minPayment, copyConfig.minPayment);
        assertEquals(config.rate, copyConfig.rate);
        assertEquals(config.getKeysWhiteList(), copyConfig.getKeysWhiteList());
        assertEquals(config.getMaxDiskCacheAge(), copyConfig.getMaxDiskCacheAge());
        assertEquals(config.getStatsIntervalSmall(), copyConfig.getStatsIntervalSmall());
        assertEquals(config.getStatsIntervalBig(), copyConfig.getStatsIntervalBig());
        assertEquals(config.getExpriedNamesCleanupInterval(), copyConfig.getExpriedNamesCleanupInterval());
        assertEquals(config.getExpriedStorageCleanupInterval(), copyConfig.getExpriedStorageCleanupInterval());
        assertEquals(config.getHoldDuration(), copyConfig.getHoldDuration());
        assertEquals(config.getDeclinedItemExpiration(), copyConfig.getDeclinedItemExpiration());
        assertEquals(config.getAddressesWhiteList(), copyConfig.getAddressesWhiteList());
        assertEquals(config.getPositiveConsensus(), copyConfig.getPositiveConsensus());
        assertEquals(config.getResyncBreakConsensus(), copyConfig.getResyncBreakConsensus());
        assertEquals(config.getMaxItemCreationAge(), copyConfig.getMaxItemCreationAge());
        assertEquals(config.getRevokedItemExpiration(), copyConfig.getRevokedItemExpiration());
        assertEquals(config.getMaxElectionsTime(), copyConfig.getMaxElectionsTime());
        assertEquals(config.getMaxConsensusReceivedCheckTime(), copyConfig.getMaxConsensusReceivedCheckTime());
        assertEquals(config.getMaxResyncTime(), copyConfig.getMaxResyncTime());
        assertEquals(config.getMaxCacheAge(), copyConfig.getMaxCacheAge());
        assertEquals(config.getMaxNameCacheAge(), copyConfig.getMaxNameCacheAge());
        assertEquals(config.getMaxGetItemTime(), copyConfig.getMaxGetItemTime());
        assertEquals(config.getGetItemRetryCount(), copyConfig.getGetItemRetryCount());
        assertEquals(config.getNegativeConsensus(), copyConfig.getNegativeConsensus());
        assertEquals(config.getPollTime(), copyConfig.getPollTime());
        assertEquals(config.getConsensusReceivedCheckTime(), copyConfig.getConsensusReceivedCheckTime());
        assertEquals(config.getResyncTime(), copyConfig.getResyncTime());
        assertEquals(config.getCheckItemTime(), copyConfig.getCheckItemTime());
        assertEquals(config.getMaxDownloadOnApproveTime(), copyConfig.getMaxDownloadOnApproveTime());
        assertEquals(config.getResyncThreshold(), copyConfig.getResyncThreshold());
        assertEquals(config.getKnownSubContractsToResync(), copyConfig.getKnownSubContractsToResync());
        assertEquals(config.getUIssuerKeys(), copyConfig.getUIssuerKeys());
        assertEquals(config.getNetworkReconfigKeyAddress(), copyConfig.getNetworkReconfigKeyAddress());
        assertEquals(config.getNetworkAdminKeyAddress(), copyConfig.getNetworkAdminKeyAddress());
        assertEquals(config.getAuthorizedNameServiceCenterKey(), copyConfig.getAuthorizedNameServiceCenterKey());
        assertEquals(config.getUIssuerName(), copyConfig.getUIssuerName());
        assertEquals(config.getQueryContractsLimit(), copyConfig.getQueryContractsLimit());
        assertEquals(config.isPermanetMode(), copyConfig.isPermanetMode());
        assertEquals(config.getFollowerCallbackPrice(), copyConfig.getFollowerCallbackPrice());
    }

    @Test
    public void addWhiteListKeyTest() {
        Config config = new Config();
        PrivateKey randomKey = new PrivateKey(4096);

        config.getKeysWhiteList().add(randomKey.getPublicKey());

        assertTrue(config.getKeysWhiteList().contains(randomKey.getPublicKey()));
    }

    @Test
    public void addWhiteListAddressTest() {
        Config config = new Config();
        PrivateKey randomKey = new PrivateKey(4096);
        KeyAddress keyAddr = new KeyAddress(randomKey.getPublicKey(), 0, true);

        config.getAddressesWhiteList().add(keyAddr);

        assertTrue(config.getAddressesWhiteList().contains(keyAddr));
    }
}
