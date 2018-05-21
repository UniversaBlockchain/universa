/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.Core;
import com.icodici.universa.HashId;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Base64u;
import net.sergeych.utils.Bytes;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.*;

public class Config {


    private KeyAddress networkAdminKeyAddress = null;

    public Config () {
        System.out.println("USING REAL CONFIG");
        try {
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjQ==").getData())); //transactionUnitsIssuerKey
            //transactionUnitsIssuerKeys.add(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
            transactionUnitsIssuerKeys.add(new KeyAddress("ZNuBikFEZbw71QQAFkNQtjfkmxFAdMgveTVPMGrFwo9vQwwPVE"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            networkAdminKeyAddress = new KeyAddress("bVmSQXWM7WvUtgcitUtjRd42WRbLycvsfPaRimpSNY3yZMUrVvEHV6mwb8A2DrKnzi795kJB");
        } catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
        }
    }

    public Config copy() {
        Config config = new Config();
        config.consensusConfigUpdater = consensusConfigUpdater;
        config.maxItemCreationAge = maxItemCreationAge;
        config.revokedItemExpiration = revokedItemExpiration;
        config.maxDownloadOnApproveTime = maxDownloadOnApproveTime;
        config.declinedItemExpiration = declinedItemExpiration;
        config.maxCacheAge = maxCacheAge;
        config.maxGetItemTime = maxGetItemTime;
        synchronized (this) {
            config.negativeConsensus = negativeConsensus;
            config.positiveConsensus = positiveConsensus;
            config.resyncBreakConsensus = resyncBreakConsensus;
        }
        config.maxElectionsTime = maxElectionsTime;
        config.pollTimeMillis = new ArrayList<>(pollTimeMillis);
        config.consensusReceivedCheckTime = new ArrayList<>(consensusReceivedCheckTime);
        config.maxConsensusReceivedCheckTime = maxConsensusReceivedCheckTime;
        config.resyncTime = new ArrayList<>(resyncTime);
        config.checkItemTime = checkItemTime;
        config.maxResyncTime = maxResyncTime;
        config.transactionUnitsIssuerKeys = new HashSet<>(transactionUnitsIssuerKeys);
        config.networkConfigIssuerKeyData = networkConfigIssuerKeyData;
        config.keysWhiteList = keysWhiteList;
        config.isFreeRegistrationsLimited = isFreeRegistrationsLimited;
        config.isFreeRegistrationsAllowedFromYaml = isFreeRegistrationsAllowedFromYaml;
        return config;
    }

    private ConsensusConfigUpdater consensusConfigUpdater;

    public void setConsensusConfigUpdater(ConsensusConfigUpdater consensusConfigUpdater) {
        this.consensusConfigUpdater = consensusConfigUpdater;
    }

    public Duration getMaxDiskCacheAge() {
        return maxDiskCacheAge;
    }

    public Duration getStatsIntervalSmall() {
        return statsIntervalSmall;
    }

    public void setStatsIntervalSmall(Duration statsIntervalSmall) {
        this.statsIntervalSmall = statsIntervalSmall;
    }

    public Duration getStatsIntervalBig() {
        return statsIntervalBig;
    }

    public void setStatsIntervalBig(Duration statsIntervalBig) {
        this.statsIntervalBig = statsIntervalBig;
    }

    public Duration getExpriedStorageCleanupInterval() {
        return expriedStorageCleanupInterval;
    }

    public interface ConsensusConfigUpdater {
        void updateConsensusConfig(Config config, int nodesCount);
    }

    private Duration maxItemCreationAge = Duration.ofDays(5);
    private Duration revokedItemExpiration = maxItemCreationAge.plusDays(10);
    private TemporalAmount maxDownloadOnApproveTime = Duration.ofMinutes(5);

    public static <T> Class<T> forceInit(Class<T> klass) {
        try {
            Class.forName(klass.getName(), true, klass.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);  // Can't happen
        }
        return klass;
    }

    public Duration getDeclinedItemExpiration() {
        return declinedItemExpiration;
    }

    public void setDeclinedItemExpiration(Duration declinedItemExpiration) {
        this.declinedItemExpiration = declinedItemExpiration;
    }

    private Duration declinedItemExpiration = Duration.ofDays(10);
    private Duration maxCacheAge = Duration.ofMinutes(20);
    private Duration maxDiskCacheAge = Duration.ofMinutes(40);
    private Duration statsIntervalSmall = Duration.ofSeconds(30);
    private Duration statsIntervalBig = Duration.ofSeconds(3600);
    private Duration maxGetItemTime = Duration.ofSeconds(30);
    private int getItemRetryCount = 10;
    private int negativeConsensus;
    private int positiveConsensus;
    private int resyncBreakConsensus;
    private Duration maxElectionsTime = Duration.ofMinutes(15);
    private List<Integer> pollTimeMillis = Arrays.asList(1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private List<Integer> consensusReceivedCheckTime = Arrays.asList(1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private Duration maxConsensusReceivedCheckTime = Duration.ofMinutes(15);
    private List<Integer> resyncTime = Arrays.asList(1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private Duration checkItemTime = Duration.ofMillis(200);
    private Duration maxResyncTime = Duration.ofMinutes(5);
    private Duration expriedStorageCleanupInterval = Duration.ofMinutes(5);

    private Boolean isFreeRegistrationsLimited = null;
    private boolean isFreeRegistrationsAllowedFromYaml = false;

    public void addTransactionUnitsIssuerKeyData(KeyAddress transactionUnitsIssuerKey) {
        this.transactionUnitsIssuerKeys.add(transactionUnitsIssuerKey);
    }

    private Set<KeyAddress> transactionUnitsIssuerKeys = new HashSet<>();

    public List<PublicKey> getKeysWhiteList() {
        return keysWhiteList;
    }

    private List<PublicKey> keysWhiteList = new ArrayList<>();

    private Bytes networkConfigIssuerKeyData = Bytes.fromHex("1E 08 1C 01 00 01 C4 00 01 83 D8 9D 79 7E 80 DD 69 3D 0A EC 27 66 B6 A4 5D DB E1 60 38 88 88 ED 07 03 E6 16 98 B0 2B 71 9B E1 85 A1 8C AF 0D 62 D6 60 3A 4B D2 FA 34 F0 2E 85 87 19 CE 6F 0C C6 DC 2B D5 11 12 C8 9A A6 F8 71 70 53 EE 3D B3 4C 97 1E 10 89 B1 77 2F 2B 6D D8 C7 B3 44 A4 8A E9 1A 42 AD F4 E0 82 74 11 A1 42 49 6C D1 87 35 94 10 66 19 80 AB 4A 13 27 B4 F0 BD C5 8F 43 25 9E 2C 6C CB 81 3C 85 10 CE 99 D6 2D 88 11 01 B6 5B F8 AB 99 15 70 08 AF B8 51 3B 4A CD 4D 9E A1 13 9C E9 EA 83 F0 95 02 E1 F6 10 72 E8 2B 2F 64 3F FB DC 27 F6 5A D2 83 BA 71 C3 D6 A2 AE 41 4F CA AA BB AA 54 C3 2F D9 F7 7A 64 AA 3A F7 67 AC 5A CA AA 20 08 90 CE D8 35 FA C0 2B 02 17 F4 0A BF 25 85 17 F9 DC 6E 6B 9D D8 A2 43 1E D1 0E CD 4F F4 FA 75 C1 62 BD 7B DD D4 2F 52 85 E0 FA 55 C7 B7 BB 4B 39 EB 08 74 C4 77");

    public static String tuTemplatePath = "./src/test_contracts/TUTemplate.yml";
    public static String testTUTemplatePath = "./src/test_contracts/TestTUTemplate.yml";
    public static String tuKeyPath = "./src/test_contracts/keys/tu_key.private.unikey";

    public static int maxExpirationMonthsInTestMode = 12;

    public static int maxCostTUInTestMode = 3;

    public static int quantiser_quantaPerU = 200;

    // num of KD (kilobytes and days) for one U
    public static int kilobytesAndDaysPerU = 1;

    public static int getMinSlotPayment() {
        return minSlotPayment;
    }

    private static int minSlotPayment = 100;

    public static Duration validUntilTailTime = Duration.ofMinutes(5);

    private String tuIssuerName = "Universa Reserve System";

    /**
     * num of known (approved, declined, revoked or locked) subcontracts of a complex contract that starts resync if some another contracts is unknown
     */
    private int knownSubContractsToResync = 1;

    public int getPositiveConsensus() {
        return positiveConsensus;
    }

    public void setPositiveConsensus(int positiveConsensus) {
        this.positiveConsensus = positiveConsensus;
    }

    public int getResyncBreakConsensus() {
        return resyncBreakConsensus;
    }

    public void setResyncBreakConsensus(int resyncBreakConsensus) {
        this.resyncBreakConsensus = resyncBreakConsensus;
    }

    public Duration getMaxItemCreationAge() {
        return maxItemCreationAge;
    }

    public Duration getRevokedItemExpiration() {
        return revokedItemExpiration;
    }

    public Duration getMaxElectionsTime() {
        return maxElectionsTime;
    }

    public Duration getMaxConsensusReceivedCheckTime() {
        return maxConsensusReceivedCheckTime;
    }

    public Duration getMaxResyncTime() {
        return maxResyncTime;
    }

    public void setMaxResyncTime(Duration time) {
        maxResyncTime = time;
    }

    public void setMaxElectionsTime(Duration maxElectionsTime) {
        this.maxElectionsTime = maxElectionsTime;
    }

    public Duration getMaxCacheAge() {
        return maxCacheAge;
    }

    public void setMaxCacheAge(Duration maxCacheAge) {
        this.maxCacheAge = maxCacheAge;
    }

    public Duration getMaxGetItemTime() {
        return maxGetItemTime;
    }

    public int getGetItemRetryCount() {
        return getItemRetryCount;
    }

    public void setMaxGetItemTime(Duration maxGetItemTime) {
        this.maxGetItemTime = maxGetItemTime;
    }

    public int getNegativeConsensus() {
        return negativeConsensus;
    }

    public void setNegativeConsensus(int negativeConsensus) {
        this.negativeConsensus = negativeConsensus;
    }

    public List<Integer> getPollTime() {
        return pollTimeMillis;
    }

    public void setPollTime(List<Integer> pollTimeMillis) {
        this.pollTimeMillis = new ArrayList<>(pollTimeMillis);
    }

    public List<Integer> getConsensusReceivedCheckTime() {
        return consensusReceivedCheckTime;
    }

    public void setConsensusReceivedCheckTime(List<Integer> consensusReceivedCheckTime) {
        this.consensusReceivedCheckTime = new ArrayList<>(consensusReceivedCheckTime);
    }

    public List<Integer> getResyncTime() {
        return resyncTime;
    }

    public Duration getCheckItemTime() {
        return checkItemTime;
    }

    public void setResyncTime(List<Integer> resyncTime) {
        this.resyncTime = new ArrayList<>(resyncTime);
    }

    public TemporalAmount getMaxDownloadOnApproveTime() {
        return maxDownloadOnApproveTime;
    }

    public void setMaxDownloadOnApproveTime(TemporalAmount maxDownloadOnApproveTime) {
        this.maxDownloadOnApproveTime = maxDownloadOnApproveTime;
    }

    public int getResyncThreshold() {
        int n = getNegativeConsensus() * 2;
        if (n > getPositiveConsensus())
            n = getNegativeConsensus();
        return n;
    }

    /**
     * Num of known (approved, declined, revoked or locked) subcontracts of a complex contract that starts resync
     * if some another contracts is unknown
     *
     * @return num of known subcontracts
     */
    public int getKnownSubContractsToResync() {
        return knownSubContractsToResync;
    }

    public Set<KeyAddress> getTransactionUnitsIssuerKeys() {
        return transactionUnitsIssuerKeys;
    }

    public PublicKey getNetworkConfigIssuerKey() {
        try {
            return new PublicKey(networkConfigIssuerKeyData.getData());
        } catch (EncryptionError e) {
            return null;
        }
    }

    public KeyAddress getNetworkAdminKeyAddress() {
        return networkAdminKeyAddress;
    }

    public String getTUIssuerName() {
        return tuIssuerName;
    }

    public boolean updateConsensusConfig(int nodesCount) {
        synchronized (this) {
            if (consensusConfigUpdater != null) {
                consensusConfigUpdater.updateConsensusConfig(this, nodesCount);
                return true;
            }
            return false;
        }
    }

    public void setIsFreeRegistrationsAllowedFromYaml(boolean val) {
        isFreeRegistrationsAllowedFromYaml = val;
        isFreeRegistrationsLimited = null;
    }

    public Boolean limitFreeRegistrations() {
        if (isFreeRegistrationsLimited == null) {
            isFreeRegistrationsLimited = new Boolean(true);

            if (Core.VERSION.contains("private"))
                isFreeRegistrationsLimited = new Boolean(false);
            else if (isFreeRegistrationsAllowedFromYaml)
                isFreeRegistrationsLimited = new Boolean(false);
        }
        return isFreeRegistrationsLimited;
    }
}
