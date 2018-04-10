package com.icodici.universa.node2;

import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Ledger;

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

    public Integer smallIntervalApproved = 0;
    public Integer bigIntervalApproved = 0;
    public Integer uptimeApproved = 0;
    public int lastMonthPaidAmount;
    public int thisMonthPaidAmount;
    public int yesterdayPaidAmount;
    public int todayPaidAmount;

    public void collect(Ledger ledger, Config config) {
        ZonedDateTime now = ZonedDateTime.now();
        Map<ItemState, Integer> lastIntervalStats = ledger.getLedgerSize(lastStatsBuildTime);
        ledgerStatsHistory.addLast(lastIntervalStats);
        ledgerHistoryTimestamps.addLast(lastStatsBuildTime);

        smallIntervalApproved = lastIntervalStats.getOrDefault(ItemState.APPROVED,0)+lastIntervalStats.getOrDefault(ItemState.REVOKED,0);
        bigIntervalApproved += smallIntervalApproved;
        uptimeApproved += smallIntervalApproved;

        lastIntervalStats.keySet().forEach(is -> ledgerSize.put(is, ledgerSize.getOrDefault(is,0) + lastIntervalStats.get(is)));

        while (ledgerHistoryTimestamps.getFirst().plus(config.getStatsIntervalBig()).isBefore(now)) {
            ledgerHistoryTimestamps.removeFirst();
            bigIntervalApproved -= ledgerStatsHistory.removeFirst().get(ItemState.APPROVED);
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
    }

    public void init(Ledger ledger) {
        nodeStartTime = ZonedDateTime.now();
        lastStatsBuildTime = nodeStartTime;
        ledgerSize = ledger.getLedgerSize(null);
    }
}
