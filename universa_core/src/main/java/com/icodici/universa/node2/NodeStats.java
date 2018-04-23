package com.icodici.universa.node2;

import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Ledger;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.Map;

public class NodeStats {
    public ZonedDateTime lastStatsBuildTime;
    public ZonedDateTime nodeStartTime;
    public Map<ItemState, Integer> ledgerSize;

    private LinkedList<Map<ItemState,Integer>> ledgerStatsHistory = new LinkedList<>();
    private LinkedList<ZonedDateTime> ledgerHistoryTimestamps = new LinkedList<>();

    public int smallIntervalApproved;
    public int bigIntervalApproved;
    public int uptimeApproved;
    public int lastMonthPaidAmount;
    public int thisMonthPaidAmount;
    public int yesterdayPaidAmount;
    public int todayPaidAmount;
    private Duration bigInterval;
    private Duration smallInterval;

    public boolean collect(Ledger ledger, Config config) {
        if(!config.getStatsIntervalSmall().equals(smallInterval) || !config.getStatsIntervalBig().equals(bigInterval)) {
            //intervals changed. need to reset node
            init(ledger,config);
            return false;
        }

        ZonedDateTime now = ZonedDateTime.now();
        Map<ItemState, Integer> lastIntervalStats = ledger.getLedgerSize(lastStatsBuildTime);
        ledgerStatsHistory.addLast(lastIntervalStats);
        ledgerHistoryTimestamps.addLast(lastStatsBuildTime);

        smallIntervalApproved = lastIntervalStats.getOrDefault(ItemState.APPROVED,0)+lastIntervalStats.getOrDefault(ItemState.REVOKED,0);
        bigIntervalApproved += smallIntervalApproved;
        uptimeApproved += smallIntervalApproved;

        lastIntervalStats.keySet().forEach(is -> ledgerSize.put(is, ledgerSize.getOrDefault(is,0) + lastIntervalStats.get(is)));

        while (ledgerHistoryTimestamps.getFirst().plus(bigInterval).isBefore(now)) {
            ledgerHistoryTimestamps.removeFirst();
            bigIntervalApproved -= ledgerStatsHistory.removeFirst().get(ItemState.APPROVED) + lastIntervalStats.getOrDefault(ItemState.REVOKED,0);
        }

        lastStatsBuildTime = now;


        lastMonthPaidAmount = 0;
        thisMonthPaidAmount = 0;
        yesterdayPaidAmount = 0;
        todayPaidAmount = 0;

        int firstDayOfThisMonth = (int) now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth()-1).toEpochSecond();
        int today = (int) now.truncatedTo(ChronoUnit.DAYS).toEpochSecond();
        int yesterday = (int) now.truncatedTo(ChronoUnit.DAYS).minusDays(1).toEpochSecond();

        Map<Integer, Integer> payments = ledger.getPayments(now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth()-1).minusMonths(1));
        payments.keySet().forEach( day -> {
            if(day < firstDayOfThisMonth) {
                lastMonthPaidAmount += payments.get(day);
            } else {
                thisMonthPaidAmount += payments.get(day);
            }

            if(day == yesterday) {
                yesterdayPaidAmount += payments.get(day);
            } else if(day == today) {
                todayPaidAmount += payments.get(day);
            }
        });
        return true;
    }

    public void init(Ledger ledger, Config config) {
        ledgerStatsHistory.clear();
        ledgerHistoryTimestamps.clear();

        smallIntervalApproved = 0;
        bigIntervalApproved = 0;
        uptimeApproved = 0;
        lastMonthPaidAmount = 0;
        thisMonthPaidAmount = 0;
        yesterdayPaidAmount = 0;
        todayPaidAmount = 0;

        bigInterval = config.getStatsIntervalBig();
        smallInterval = config.getStatsIntervalSmall();
        nodeStartTime = ZonedDateTime.now();
        lastStatsBuildTime = nodeStartTime;
        ledgerSize = ledger.getLedgerSize(null);
    }
}
