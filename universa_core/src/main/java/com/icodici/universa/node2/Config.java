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
import com.icodici.universa.Core;
import com.icodici.universa.contract.services.NSmartContract;
import net.sergeych.utils.Base64u;
import net.sergeych.utils.Bytes;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.*;

public class Config {


    private KeyAddress networkAdminKeyAddress = null;
    private KeyAddress networkReconfigKeyAddress = null;
    public Map<String,Integer> minPayment = new HashMap<>();
    public Map<String,Double> rate = new HashMap<>();

    public Config () {
        System.out.println("USING REAL CONFIG");
        try {
            networkAdminKeyAddress = new KeyAddress("bVmSQXWM7WvUtgcitUtjRd42WRbLycvsfPaRimpSNY3yZMUrVvEHV6mwb8A2DrKnzi795kJB");
            networkReconfigKeyAddress = new KeyAddress("JPgxNXkRSYNnWM82D8WKLSH3d98jFeEeCmDN4wLfzfi5kE6kvfopJUQrbDczrgpCqpo5ncG8");
            authorizedNameServiceCenterKey = new PublicKey(Base64u.decodeCompactString("HggcAQABxAABg9ideX6A3Wk9CuwnZrakXdvhYDiIiO0HA+YWmLArcZvhhaGMrw1i1mA6S9L6NPAuhYcZzm8Mxtwr1RESyJqm+HFwU+49s0yXHhCJsXcvK23Yx7NEpIrpGkKt9OCCdBGhQkls0Yc1lBBmGYCrShMntPC9xY9DJZ4sbMuBPIUQzpnWLYgRAbZb+KuZFXAIr7hRO0rNTZ6hE5zp6oPwlQLh9hBy6CsvZD/73Cf2WtKDunHD1qKuQU/KqruqVMMv2fd6ZKo692esWsqqIAiQztg1+sArAhf0Cr8lhRf53G5rndiiQx7RDs1P9Pp1wWK9e93UL1KF4PpVx7e7SznrCHTEdw"));

            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjQ==").getData())); //transactionUnitsIssuerKey
            transactionUnitsIssuerKeys.add(new KeyAddress("ZNuBikFEZbw71QQAFkNQtjfkmxFAdMgveTVPMGrFwo9vQwwPVE"));
            addressesWhiteList.add(new KeyAddress("JguevMekFzsM8Co2bqrswrVim9c9WsNxG9thLeCcNxncBcHVsnziRjhzEbhwDnL3wj2hha6H"));
        } catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }

        rate.put(NSmartContract.SmartContractType.SLOT1.name(),1.0);
        rate.put(NSmartContract.SmartContractType.UNS1.name(), 0.25);

        minPayment.put(NSmartContract.SmartContractType.SLOT1.name(),100);
        minPayment.put(NSmartContract.SmartContractType.UNS1.name(), (int) Math.ceil(365/rate.get(NSmartContract.SmartContractType.UNS1.name())));
    }

    public Config copy() {
        Config config = new Config();
        config.consensusConfigUpdater = consensusConfigUpdater;
        config.maxItemCreationAge = maxItemCreationAge;
        config.revokedItemExpiration = revokedItemExpiration;
        config.maxDownloadOnApproveTime = maxDownloadOnApproveTime;
        config.declinedItemExpiration = declinedItemExpiration;
        config.maxCacheAge = maxCacheAge;
        config.maxNameCacheAge = maxNameCacheAge;
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


    public Duration getExpriedNamesCleanupInterval() {
        return expriedNamesCleanupInterval;
    }

    public Duration getExpriedStorageCleanupInterval() {
        return expriedStorageCleanupInterval;
    }

    public Duration getHoldDuration() {
        return holdDuration;
    }

    public void setHoldDuration(Duration holdDuration) {
        this.holdDuration = holdDuration;
    }

    public void setRate(String name, double value) {
        rate.put(name,value);
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
    private Duration maxNameCacheAge = Duration.ofMinutes(5);
    private Duration statsIntervalSmall = Duration.ofSeconds(30);
    private Duration statsIntervalBig = Duration.ofSeconds(3600);
    private Duration maxGetItemTime = Duration.ofSeconds(30);
    private int getItemRetryCount = 10;
    private int negativeConsensus;
    private int positiveConsensus;
    private int resyncBreakConsensus;
    private Duration maxElectionsTime = Duration.ofMinutes(15);
    private List<Integer> pollTimeMillis = Arrays.asList(0,1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private List<Integer> consensusReceivedCheckTime = Arrays.asList(0,1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private Duration maxConsensusReceivedCheckTime = Duration.ofMinutes(15);
    private List<Integer> resyncTime = Arrays.asList(0,1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private Duration checkItemTime = Duration.ofMillis(200);
    private Duration maxResyncTime = Duration.ofMinutes(5);
    private Duration expriedStorageCleanupInterval = Duration.ofMinutes(5);
    private Duration expriedNamesCleanupInterval = Duration.ofMinutes(5);
    private Duration holdDuration = Duration.ofDays(30);

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

    public List<KeyAddress> getAddressesWhiteList() {
        return addressesWhiteList;
    }

    private List<KeyAddress> addressesWhiteList = new ArrayList<>();

    private PublicKey authorizedNameServiceCenterKey = null;

    public static String uTemplatePath = "./src/test_contracts/UTemplate.yml";
    public static String testUTemplatePath = "./src/test_contracts/TestUTemplate.yml";
    public static String uKeyPath = "./src/test_contracts/keys/u_key.private.unikey";

    public static int maxExpirationMonthsInTestMode = 12;

    public static int maxCostUInTestMode = 3;

    public static int quantiser_quantaPerU = 200;


    public int getMinPayment(String extendedType)
    {
        return minPayment.get(extendedType);
    }

    public double getRate(String extendedType)
    {
        return rate.get(extendedType);
    }

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

    public Duration getMaxNameCacheAge() {
        return maxNameCacheAge;
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

    public Set<KeyAddress> getUIssuerKeys() {
        return transactionUnitsIssuerKeys;
    }


    public KeyAddress getNetworkReconfigKeyAddress() {
        return networkReconfigKeyAddress;
    }

    public KeyAddress getNetworkAdminKeyAddress() {
        return networkAdminKeyAddress;
    }


    public void setAuthorizedNameServiceCenterKeyData(Bytes authorizedNameServiceCenterKeyData) {
        try {
            this.authorizedNameServiceCenterKey = new PublicKey(authorizedNameServiceCenterKeyData.getData());
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }

    public PublicKey getAuthorizedNameServiceCenterKey() {
        return authorizedNameServiceCenterKey;

    }

    public String getUIssuerName() {
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
