/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import net.sergeych.utils.Bytes;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.List;

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
    private int resyncBreakConsensus;
    private Duration maxElectionsTime = Duration.ofMinutes(15);
    private Duration pollTime = Duration.ofMillis(1000);
    private Duration consensusReceivedCheckTime = Duration.ofMillis(1000);
    private Duration maxConsensusReceivedCheckTime = Duration.ofMinutes(15);
    private Duration resyncTime = Duration.ofMillis(1000);
    private Duration checkItemTime = Duration.ofMillis(200);
    private Duration maxResyncTime = Duration.ofMinutes(5);
    private Bytes transactionUnitsIssuerKeyData = Bytes.fromHex("1E 08 1C 01 00 01 C4 00 02 CC 71 3D 89 B5 A5 9C AE 11 CE 98 05 20 1E 23 22 0D D6 71 76 52 29 73 40 A8 E4 D8 52 D1 97 63 4D F1 25 2C 29 A8 4F B2 FC 76 EA A8 4D 16 B2 67 00 B7 96 B4 17 91 FB 6F 3F 3B 53 2F 0C 7B 55 1D 20 37 0E 26 03 FC 1C C1 76 33 42 3F 92 44 FA 77 9F 59 85 81 FF 4E 5A 43 72 92 EF 9C 95 84 95 90 1F 3B 2B 5A 20 21 34 D5 B9 0E 91 FE 8C CF 97 A0 A5 3A 03 10 43 D9 1F F4 57 8A 08 FD C2 44 97 92 4E 37 59 94 B1 55 8E 5F DB 36 95 9A 26 D4 67 D5 4B A0 99 66 8C D4 D9 FA EB 49 51 98 BA EA 2B BD 7B 44 97 BA 93 AB FF E0 49 2F 51 A6 E7 DE 70 DF 61 5D D3 5E 35 90 1B C0 1D 80 C7 CF 62 B7 C7 89 B5 A0 50 1C 12 52 7C DD A8 52 97 5B BC E7 F9 DA 21 A7 F6 CA EC 62 40 DC F7 B8 53 13 9F 42 29 50 B0 8C BC B3 2F 98 4F 8B F6 2A 3B F8 A5 97 95 DD AE B2 E8 CC 5C 8D 72 70 34 70 F3 98 7A 20 99 4F 70 A1 D5 40 A9 C9 FF 8C 0D 05 4B DE 04 32 C5 2E 53 9C 32 90 B3 5A 53 BF 0F B3 36 23 23 5A F1 71 2F C0 F6 85 26 53 75 50 DF FD 79 5A 5A 45 40 44 7C 3A BA CE 2F 91 0B C3 1A 55 6C C5 6C D8 BD D7 7B 2E 63 44 3C 17 B7 A3 29 1E 9A 42 F4 0F B8 D3 7A 6C AE A2 45 86 5C 87 17 BF 17 05 88 5C EA F4 0B 4C 80 DC D1 60 C4 A2 E2 29 98 E4 77 97 89 F3 81 8B CB E8 83 D3 44 73 8E 89 BE 18 6E 05 0D 02 AB D8 7C 58 64 37 43 B8 B1 38 3C 4A 58 4B 31 01 FF C2 F2 22 A7 8C ED D4 B2 68 71 53 04 82 3F B0 BB 85 AE EE 9F 7E 82 B1 04 D9 3F 73 00 06 66 A3 B0 0F 77 E4 26 33 6A D5 1D E1 E9 0C A5 59 72 3F 71 23 BD C9 19 D1 32 63 01 27 06 A5 31 EC 4E 73 7F E4 3C 41 1D 7E 31 FF 19 C2 54 D0 CC 3A 0E 0A 18 F1 08 1F 68 DC 17 70 56 C1 52 F7 F7 4F 06 79 33 2B 1F DB 53 8B 1C 7D 77 83 C8 33 44 49 BE 87 C7");

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

    public Duration getConsensusReceivedCheckTime() {
        return consensusReceivedCheckTime;
    }

    public void setConsensusReceivedCheckTime(Duration consensusReceivedCheckTime) {
        this.consensusReceivedCheckTime = consensusReceivedCheckTime;
    }

    public Duration getResyncTime() {
        return resyncTime;
    }

    public Duration getCheckItemTime() {
        return checkItemTime;
    }

    public void setResyncTime(Duration resyncTime) {
        this.resyncTime = resyncTime;
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
     * num of known (approved, declined, revoked or locked) subcontracts of a complex contract that starts resync
     * if some another contracts is unknown
     */
    public int getKnownSubContractsToResync() {
        return knownSubContractsToResync;
    }

    public PublicKey getTransactionUnitsIssuerKey() {
        try {
            return new PublicKey(transactionUnitsIssuerKeyData.getData());
        } catch (EncryptionError e) {
            return null;
        }
    }
}
