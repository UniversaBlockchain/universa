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
import net.sergeych.utils.Bytes;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.*;

public class Config {


    public Config () {
        System.out.println("USING TEST CONFIG");
        try {
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjQ==").getData())); //transactionUnitsIssuerKey
            keysWhiteList.add(getNetworkAdminKey());
            //transactionUnitsIssuerKeys.add(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
            transactionUnitsIssuerKeys.add(new KeyAddress("ZNuBikFEZbw71QQAFkNQtjfkmxFAdMgveTVPMGrFwo9vQwwPVE"));

            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABzD/Kgn3Nn2F/nhZOVspa7LEwiHYnr6PGID4+6itQcag5eY6lRgpg/MGMT0J3SyKREFo0vrE0q8OHOe8pqBEv+m24Vo3dRT1tTOSoWPpGc8xXYj9Y0jeeMyxVzx2uvtXM3SHzS7YaP4Y1yhi1LnVq1MpQ9cls9KL+f8AJCuqZ8meCc2Zxo/CgEue24v7z+UvB9eq8jiCJM3BW6hDPW4bxYp9nbZib2AuMqzVK/DP21R5oVdxfWYKd0+9E7VfAk++J1/JtUbO4Q185ic2QEvhgqYnw4JJTTxeglqXkx6Ge5iaEJ+GqiwnQ/+K1huX33vMRMvC0BbpwtTrvKHCOKa+hwQ==").getData())); //TestKeys.publicKey(0)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABt4ri+JZn6f1RDUaymNkuMfc7dHaeYk4rr8RawGu7h6XVfRahSDqEcud1ht+oqJqm2TYmrO2ZEP8zjENQi92PiJ89yh3mzzD9BjtEcstBkDgE30i4jiYqPpkWORq51uR6xPMAI6qVO8sXoPRANCIyXejszh0meYCCrwl2uJDqKrOGYP010FKjkDy9AyC4Vep/6rppgKqsUukydzTX/laYj0179w5hR2BiPPJuY/uAef6AsdRXfdQm0McsmV9lcu0b9svEBfu6vDA9jmMlz68KNSXBpX6VrMBgp+fMWcs1D/hzwZZh3tCMUbPQiInPtiLY5DSlZ1MUEPqGNGcbRYPEPQ==").getData())); //TestKeys.publicKey(1)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABp+gb/K7GyhtNNfLYbc4geKyInuNsoIw5mSZOarqnwx3rqFwKzJKyIQ6C9XyzyJWl9y7jO8VtmbWqvbG/puAhSmC2Gizl012fZr72so1YkEDJSLghOld9s0L/SZ2SIsJmS1Dq4jKaiXRkVuV2+7P8yhhVY/8J2HGwEi1veBqzqFPQGC39F2nMtdAh03UNGDAfImprIdyEFy6gyeDPNY4tklFHfJJb/xk7Tt8oaUuQbW18iK0Az4IGXK//tYguAwQTqslbi/qz919aJgtubQlM8pOu7LAQPHsYhjnKRiml217G/C2yy/cgGXb+eEHbtTWHsjoEBGPnaV9HQQK0PQ+WfQ==").getData())); //TestKeys.publicKey(2)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAAB9WICC8+q5kEF/Y0Eg1GHeq89FZuYM4Ejo/CyjZqUXv0mi7rMWwAbm2Q952Z76x34l2zrnreyhDSqpTVspYlQeagxFtZVTCx0gqOzzWllggEBzJGJ9ycjpJmJZgukCTxXMqns7tUPEmkBIwrk09AQ2hh/KeTgib2SniFhNDFnYl+A1S92p/gWW/46kzLkWk70F2lKRVpR7a5/ECyN3n/BJeJDesWQfj4splxE5f+DmekDhALMXE3ImzZQyJjofefQFnIZIktKF3m6Ob4eD5JF4FR6Zt+Z9oum9oz91zyNGNqMurKRjJyUHvUZLfCVBJLIM80OMk9Y6jZ3DHZcWbsLEw==").getData())); //TestKeys.publicKey(3)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABpNHyeIAYbyLD6mvt/xQFTTMZ8FMFoHw6bedzQk85NhJcuZwlgUHin0gBhsQ6MINKU3SffjwUcrehhGjmeM0QJyqsT2dDV9S+0uVFkHQ5bKD8+oxG4h7T3cSm5g6H+8iPytU2jV622uuIKgGjoRv5WZju0YfzhFnepV1mruwngnEMeg/5jpiH/zfFHfvcTQ7vpdk5MHZofxrpZeRX15pgjS06qRdw1xuPNGIW/zzrWSoFL6Yc2lrrXY60pDHt1xohSdiYa7yGhUYuQyNQMxyqlQbYZbtZ5hdUQnX8F7DQbv6uNkOwOrxjNJVt7bz34wVhiQ1mdbxrAZwbujPJxGWrbw==").getData())); //TestKeys.publicKey(4)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABqecLPVcOeBlRDx9ddQGk9rkakh8N7w7ZfVdDjkkkhIKDLn6ycwszEaQxsN1x+i+3iLyqiX2y6Yuujk0Mk5M5p1ID1b7pJILF2P/0yHINAPey9L7dy1yPQEeampn6dYq9PKQsEWdJ5iw32ljSI2uo7ZLanixfK3rQazCwFrnIT8vXqw1MaWXd645LrbuVglCEx3qVlWXir2eLW1TmqbhhmjuNWEBjHgX2uhcNScFlDhTfgT9DtNNtgrNdN2QzkYoD29RvuwOBzwCaRXvN0PB1U+HkZIRbYTBvt3wbm8LjWi9ZWawoTcilPSYl3KSCWZVMc9FCCxjFaN1eqgobKRzguQ==").getData())); //TestKeys.publicKey(5)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAAB40pSF0cSXAJm/igpktEga7qArVyyPeGEyRMsr12R0EMTx+sZUSNCgS9un3WgOKi0W0usQm6f6YFW7YB7BBu6+kD1txye+RELRK/VE0Fo7uOFuXtNRLb7FwjJRlnlFPHyKcmLkYU+KK3HZ8dbWmlnbEhnxLQBvKNxUbMdOUvonYX4IsRAUh+GTliGrhtUuFqlUbz6suNvcooBjQ2eCSssYzyO1MydGjQVrXMh2l6FUwaanj5IXzn44ZUcCifk+Idm1UJkqveS+YgIiSXlWUNo6py/1r+/jVQ9oVwvFMaJz/3r0pE9S/AkdQadjVnIMxkhzK0QHyFsjy3qnjs8IVL1Yw==").getData())); //TestKeys.publicKey(6)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABgcoWwUfPcbGvFAIw6bKRNgVmJ624eBR7AqYeWHXeqf2jHnJV8N9yFQLX+YJa0hUEjsEt5zVCnB77leVdyfppv80JMlGrp8bcXFEGhmcQOrB4V/6ZsT3RFo5QLoFXj4mJTaClbtB7Py0PhJ8bvgbhOyBLM1NzcWfNTxQHCjXEedhJuolOkL2zXg0VVLCyLR6EsKewEXe299MGP59/+ZdhJzQI8qEYN2sTw+SHj5XXvaBqCbAucWjA76HjDkylkfBfnP9kKZFl2rtudqdaUFoSN9axiQBdwwCz2x8n3XBdBoPtv7QUuRD7CSYx7O6cWaixgmVxRrmJ3hyt+D8+qTnp0w==").getData())); //TestKeys.publicKey(7)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAAB6F24Fr7LIs10ngX7/ZH+WMch1Ml7diRARe3lFkesB0HAhdSiS+fDHVOuT8tTVwgGNp++2dnWxFgHwNxdV5Us0ODkEtTqIHivoQzr0Mm1Q4zNoXSTs82lnvqFRArkh7Jg6V+a07ESm8khIEykX817ip8E40t+7TdQSrCk9SbRwOguQvupU77X0arT4ntDHvSFC1nyEaLiSyqI5vLQWjIxBqj7yqM4SnwSpFQtR6dzB6hhtW7HI1o82k9pBANVgEa5mNhlNVMXy4cKxm/fGlDC72VuEIT9E6Otsz8zptxK6nZSj7fG+PG9CoI5wlgLWXlSq0YjPSTZJpq5VJ3sqceW2Q==").getData())); //TestKeys.publicKey(8)
            keysWhiteList.add(new PublicKey(Bytes.fromBase64("HggcAQABxAABrnHlqtQQSQX7VxYzxTE6HI/sm1odbK19+IvJJ0KDR/SHxUJ/sy9qURBKeLwfKsu3NTef/gwizitAVvdSizFa3EUBNnT/VrMWlLKSWJJMwhh0c7c4tz+3Elag0RznYnMV4VE7LWUj4mONZPTIyT8YubyBZu6I7wn7b5pDHK6SVJcfYEA4VvipoPRHqFgJ3Devqe+iqlqffbPyekZd/B9kwnKWME4+/ez+I2IiVCHU6xtY3ucirQxMQ892t7topZHWJXliga2vOc7tRVRJYqC64Yj19Ujr787dWlWR7IoIIa/KnN4PCp5FiaDp4UVHeG6RfYIHCWNpv+Kc2/UWApIJhQ==").getData())); //TestKeys.publicKey(9)
        } catch (Exception e) {
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
    private Bytes networkAdminKeyData = Bytes.fromHex("1E 08 1C 01 00 01 C4 00 01 83 D8 9D 79 7E 80 DD 69 3D 0A EC 27 66 B6 A4 5D DB E1 60 38 88 88 ED 07 03 E6 16 98 B0 2B 71 9B E1 85 A1 8C AF 0D 62 D6 60 3A 4B D2 FA 34 F0 2E 85 87 19 CE 6F 0C C6 DC 2B D5 11 12 C8 9A A6 F8 71 70 53 EE 3D B3 4C 97 1E 10 89 B1 77 2F 2B 6D D8 C7 B3 44 A4 8A E9 1A 42 AD F4 E0 82 74 11 A1 42 49 6C D1 87 35 94 10 66 19 80 AB 4A 13 27 B4 F0 BD C5 8F 43 25 9E 2C 6C CB 81 3C 85 10 CE 99 D6 2D 88 11 01 B6 5B F8 AB 99 15 70 08 AF B8 51 3B 4A CD 4D 9E A1 13 9C E9 EA 83 F0 95 02 E1 F6 10 72 E8 2B 2F 64 3F FB DC 27 F6 5A D2 83 BA 71 C3 D6 A2 AE 41 4F CA AA BB AA 54 C3 2F D9 F7 7A 64 AA 3A F7 67 AC 5A CA AA 20 08 90 CE D8 35 FA C0 2B 02 17 F4 0A BF 25 85 17 F9 DC 6E 6B 9D D8 A2 43 1E D1 0E CD 4F F4 FA 75 C1 62 BD 7B DD D4 2F 52 85 E0 FA 55 C7 B7 BB 4B 39 EB 08 74 C4 77");

    public static String tuTemplatePath = "./src/test_contracts/TUTemplate.yml";
    public static String testTUTemplatePath = "./src/test_contracts/TestTUTemplate.yml";
    public static String tuKeyPath = "./src/test_contracts/keys/tu_key.private.unikey";

    public static int maxExpirationMonthsInTestMode = 12;

    public static int maxCostTUInTestMode = 3;

    public static int quantiser_quantaPerUTN = 200;

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

    public PublicKey getNetworkAdminKey() {
        try {
            return new PublicKey(networkAdminKeyData.getData());
        } catch (EncryptionError e) {
            return null;
        }
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
