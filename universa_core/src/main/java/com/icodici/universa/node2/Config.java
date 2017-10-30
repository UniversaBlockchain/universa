/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

public class Config {

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
    private Duration maxGetItemTime = Duration.ofSeconds(30);
    private int negativeConsensus;
    private int positiveConsensus;
    private Duration maxElectionsTime = Duration.ofMinutes(15);
    private Duration pollTime = Duration.ofMillis(1000);

    public int getPositiveConsensus() {
        return positiveConsensus;
    }

    public void setPositiveConsensus(int positiveConsensus) {
        this.positiveConsensus = positiveConsensus;
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

    public void setMaxGetItemTime(Duration maxGetItemTime) {
        this.maxGetItemTime = maxGetItemTime;
    }

    public int getNegativeConsensus() {
        return negativeConsensus;
    }

    public void setNegativeConsensus(int negativeConsensus) {
        this.negativeConsensus = negativeConsensus;
    }

    public Duration getPollTime() {
        return pollTime;
    }

    public void setPollTime(Duration pollTime) {
        this.pollTime = pollTime;
    }

    public TemporalAmount getMaxDownloadOnApproveTime() {
        return maxDownloadOnApproveTime;
    }

    public void setMaxDownloadOnApproveTime(TemporalAmount maxDownloadOnApproveTime) {
        this.maxDownloadOnApproveTime = maxDownloadOnApproveTime;
    }
}
