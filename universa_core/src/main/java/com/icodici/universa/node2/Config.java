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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.*;

public class Config {


    private KeyAddress networkAdminKeyAddress = null;
    private KeyAddress networkReconfigKeyAddress = null;
    public Map<String, Integer> minPayment = new HashMap<>();
    public Map<String, BigDecimal> rate = new HashMap<>();
    private KeyAddress authorizedNameServiceCenterAddress;
    private Duration connectivityInfoValidityPeriod = Duration.ofMinutes(5);

    public Config () {
        System.out.println("USING REAL CONFIG");
        try {
            networkAdminKeyAddress = new KeyAddress("bVmSQXWM7WvUtgcitUtjRd42WRbLycvsfPaRimpSNY3yZMUrVvEHV6mwb8A2DrKnzi795kJB");
            networkReconfigKeyAddress = new KeyAddress("JPgxNXkRSYNnWM82D8WKLSH3d98jFeEeCmDN4wLfzfi5kE6kvfopJUQrbDczrgpCqpo5ncG8");
            authorizedNameServiceCenterAddress = new KeyAddress("bfj7QxZRtaKVnQe245MDCrnVcxrvWb5tAAhaWTcgDgHCaEjHZkHQioCSRJp2x5s3pYSH2rum");

            addressesWhiteList.add(new KeyAddress("J3uaVvHE7JqhvVb1c26RyDhfJw9eP2KR1KRhm2VdmYx7NwHpzdHTyEPjcmKpgkJAtzWLSPUw"));
            uIssuerKeys.add(new KeyAddress("ZNuBikFEZbw71QQAFkNQtjfkmxFAdMgveTVPMGrFwo9vQwwPVE"));
            uIssuerKeys.add(new KeyAddress("J3uaVvHE7JqhvVb1c26RyDhfJw9eP2KR1KRhm2VdmYx7NwHpzdHTyEPjcmKpgkJAtzWLSPUw"));
            addressesWhiteList.add(new KeyAddress("JguevMekFzsM8Co2bqrswrVim9c9WsNxG9thLeCcNxncBcHVsnziRjhzEbhwDnL3wj2hha6H"));

            //U-bank
            addressesWhiteList.add(new KeyAddress("YuY8XgTD9mwuucSku9myWyZbbJ1CY43D2KXD8obuxp73eoK5EU"));
            addressesWhiteList.add(new KeyAddress("JuDQ9auvkvLEXaudcSEYabMzSnEu6drQ3UHV3gDFuYBxusSXHSLj2DgDNCL69zw2XkzdrDmr"));

        } catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
        }

        rate.put(NSmartContract.SmartContractType.SLOT1.name(), new BigDecimal("4"));
        rate.put(NSmartContract.SmartContractType.UNS1.name(), new BigDecimal("0.25"));
        rate.put(NSmartContract.SmartContractType.UNS2.name(), new BigDecimal("0.365"));
        rate.put(NSmartContract.SmartContractType.FOLLOWER1.name(), new BigDecimal("1"));

        rate.put(NSmartContract.SmartContractType.FOLLOWER1.name() + ":callback", new BigDecimal("1"));

        minPayment.put(NSmartContract.SmartContractType.SLOT1.name(), 100);
        minPayment.put(NSmartContract.SmartContractType.UNS1.name(), (int) Math.ceil(365/rate.get(NSmartContract.SmartContractType.UNS1.name()).doubleValue()));
        minPayment.put(NSmartContract.SmartContractType.UNS2.name(), (int) Math.ceil(365/rate.get(NSmartContract.SmartContractType.UNS2.name()).doubleValue()));
        minPayment.put(NSmartContract.SmartContractType.FOLLOWER1.name(), 100);
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
        config.statsIntervalSmall = statsIntervalSmall;
        config.statsIntervalBig = statsIntervalBig;
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
        config.uIssuerKeys = new HashSet<>(uIssuerKeys);
        config.holdDuration = holdDuration;
        config.keysWhiteList = new ArrayList<>(keysWhiteList);
        config.addressesWhiteList = new ArrayList<>(addressesWhiteList);
        config.isFreeRegistrationsLimited = isFreeRegistrationsLimited;
        config.isFreeRegistrationsAllowedFromYaml = isFreeRegistrationsAllowedFromYaml;
        config.permanetMode = permanetMode;
        config.networkAdminKeyAddress = networkAdminKeyAddress;
        config.networkReconfigKeyAddress = networkReconfigKeyAddress;
        config.minPayment = new HashMap<>(minPayment);
        config.rate = new HashMap<>(rate);
        config.queryContractsLimit = queryContractsLimit;
        config.followerCallbackExpiration = followerCallbackExpiration;
        config.followerCallbackDelay = followerCallbackDelay;
        config.followerCallbackStateStoreTime = followerCallbackStateStoreTime;
        config.followerCallbackSynchronizationInterval = followerCallbackSynchronizationInterval;
        config.ratioNodesSendFollowerCallbackToComplete = ratioNodesSendFollowerCallbackToComplete;
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

    @Deprecated
    public void setRate(String name, double value) {
        rate.put(name, new BigDecimal(value));
    }

    public void setServiceRate(String name, BigDecimal value) {
        rate.put(name, value);
    }

    public void setMaxDiskCacheAge(Duration diskCacheAge) {
        maxDiskCacheAge = diskCacheAge;
    }

    public Duration getConnectivityInfoValidityPeriod() {
        return connectivityInfoValidityPeriod;
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
    private int limitRequestsForKeyPerMinute = 600;
    private int limitUbotRequestsForKeyPerMinute = 3;
    private int rateLimitDisablingPayment = 5;
    private Duration unlimitPeriod = Duration.ofMinutes(5);
    private Duration maxElectionsTime = Duration.ofMinutes(15);
    private Duration maxVoteTime = Duration.ofDays(7);
    private List<Integer> pollTimeMillis = Arrays.asList(0,1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private List<Integer> consensusReceivedCheckTime = Arrays.asList(0,1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private Duration maxConsensusReceivedCheckTime = Duration.ofMinutes(15);
    private List<Integer> resyncTime = Arrays.asList(0,1000,1000,1000,2000,4000,8000,16000,32000,60000);
    private Duration checkItemTime = Duration.ofMillis(200);
    private Duration maxResyncTime = Duration.ofMinutes(5);
    private Duration expriedStorageCleanupInterval = Duration.ofMinutes(5);
    private Duration expriedNamesCleanupInterval = Duration.ofMinutes(5);
    private Duration holdDuration = Duration.ofDays(30);
    private int paymentQuantaLimit = 200;
    private int queryContractsLimit = 100;
    private Duration followerCallbackExpiration = Duration.ofMinutes(10);
    private Duration followerCallbackDelay = Duration.ofSeconds(10);
    private Duration followerCallbackStateStoreTime = Duration.ofDays(3);
    private Duration followerCallbackSynchronizationInterval = Duration.ofHours(12);
    private BigDecimal ratioNodesSendFollowerCallbackToComplete = BigDecimal.valueOf(0.3);
    private Duration maxWaitSessionConsensus = Duration.ofSeconds(20);
    private Duration maxWaitSessionNode = Duration.ofSeconds(5);
    private Duration ubotSessionLifeTime = Duration.ofSeconds(60);

    private Boolean permanetMode = null;
    private Boolean isFreeRegistrationsLimited = null;
    private boolean isFreeRegistrationsAllowedFromYaml = false;

    public void addTransactionUnitsIssuerKeyData(KeyAddress UIssuerKey) {
        this.uIssuerKeys.add(UIssuerKey);
    }

    private Set<KeyAddress> uIssuerKeys = new HashSet<>();

    public List<PublicKey> getKeysWhiteList() {
        return keysWhiteList;
    }

    private List<PublicKey> keysWhiteList = new ArrayList<>();

    public List<KeyAddress> getAddressesWhiteList() {
        return addressesWhiteList;
    }

    private List<KeyAddress> addressesWhiteList = new ArrayList<>();


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

    @Deprecated
    public double getRate(String extendedType) {
        return rate.get(extendedType).doubleValue();
    }

    public BigDecimal getServiceRate(String extendedType) {
        return rate.get(extendedType);
    }

    public static Duration validUntilTailTime = Duration.ofMinutes(5);

    private String uIssuerName = "Universa Reserve System";

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
    public Duration getMaxVoteTime() {
        return maxVoteTime;
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

    public int getLimitRequestsForKeyPerMinute() { return limitRequestsForKeyPerMinute; }
    public int getLimitUbotRequestsForKeyPerMinute() { return limitUbotRequestsForKeyPerMinute; }

    public int getRateLimitDisablingPayment() { return rateLimitDisablingPayment; }

    public Duration getUnlimitPeriod() { return unlimitPeriod; }

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

    public int getPaymentQuantaLimit() { return paymentQuantaLimit; }

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
        return uIssuerKeys;
    }

    /**
     * @deprecated use {@link #getUIssuerKeys()} instead.
     */
    @Deprecated
    public Set<KeyAddress> getTransactionUnitsIssuerKeys() {
        return uIssuerKeys;
    }

    public KeyAddress getNetworkReconfigKeyAddress() {
        return networkReconfigKeyAddress;
    }

    public KeyAddress getNetworkAdminKeyAddress() {
        return networkAdminKeyAddress;
    }


    public void setAuthorizedNameServiceCenterKeyData(Bytes authorizedNameServiceCenterKeyData) {
        throw new IllegalArgumentException("name service key is not supported. use key address instead");
    }

    public void setAuthorizedNameServiceCenterAddress(KeyAddress authorizedNameServiceCenterAddress) {
            this.authorizedNameServiceCenterAddress = authorizedNameServiceCenterAddress;
    }

    public KeyAddress getAuthorizedNameServiceCenterAddress() {
       return authorizedNameServiceCenterAddress;
    }

    @Deprecated
    public PublicKey getAuthorizedNameServiceCenterKey() {
        throw new IllegalArgumentException("name service key is not supported. use key address instead");
    }

    public String getUIssuerName() {
        return uIssuerName;
    }

    /**
     * @deprecated use {@link #getUIssuerName()} instead.
     */
    @Deprecated
    public String getTUIssuerName() {
        return uIssuerName;
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

    public void setPermanetMode(boolean val) {
        if (permanetMode == null)
            permanetMode = val;
    }

    public Boolean isPermanetMode() {
        if (permanetMode == null)
            return false;

        return permanetMode;
    }

    public int getQueryContractsLimit() {
        return queryContractsLimit;
    }

    public void setQueryContractsLimit(int queryContractsLimit) {
        this.queryContractsLimit = queryContractsLimit;
    }

    public Duration getFollowerCallbackExpiration() {
        return followerCallbackExpiration;
    }

    public void setFollowerCallbackExpiration(Duration followerCallbackExpiration) {
        this.followerCallbackExpiration = followerCallbackExpiration;
    }

    public Duration getFollowerCallbackDelay() {
        return followerCallbackDelay;
    }

    public void setFollowerCallbackDelay(Duration followerCallbackDelay) {
        this.followerCallbackDelay = followerCallbackDelay;
    }

    public Duration getFollowerCallbackStateStoreTime() {
        return followerCallbackStateStoreTime;
    }

    public void setFollowerCallbackStateStoreTime(Duration followerCallbackStateStoreTime) {
        this.followerCallbackStateStoreTime = followerCallbackStateStoreTime;
    }

    public Duration getFollowerCallbackSynchronizationInterval() {
        return followerCallbackSynchronizationInterval;
    }

    public void setFollowerCallbackSynchronizationInterval(Duration followerCallbackSynchronizationInterval) {
        this.followerCallbackSynchronizationInterval = followerCallbackSynchronizationInterval;
    }

    @Deprecated
    public double getRateNodesSendFollowerCallbackToComplete() { return ratioNodesSendFollowerCallbackToComplete.doubleValue(); }

    @Deprecated
    public void setRateNodesSendFollowerCallbackToComplete(double rateNodesSendFollowerCallbackToComplete) {
        this.ratioNodesSendFollowerCallbackToComplete = new BigDecimal(rateNodesSendFollowerCallbackToComplete);
    }

    public BigDecimal getRatioNodesSendFollowerCallbackToComplete() { return ratioNodesSendFollowerCallbackToComplete; }

    public void setRatioNodesSendFollowerCallbackToComplete(BigDecimal ratioNodesSendFollowerCallbackToComplete) {
        this.ratioNodesSendFollowerCallbackToComplete = ratioNodesSendFollowerCallbackToComplete;
    }

    public Duration getMaxWaitSessionConsensus() {
        return maxWaitSessionConsensus;
    }

    public Duration getMaxWaitSessionNode() {
        return maxWaitSessionNode;
    }

    public Duration getUbotSessionLifeTime() {
        return ubotSessionLifeTime;
    }
}
