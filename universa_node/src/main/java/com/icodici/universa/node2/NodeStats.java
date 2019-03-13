package com.icodici.universa.node2;

import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Ledger;
import net.sergeych.tools.Binder;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
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
    private Duration bigInterval;
    private Duration smallInterval;
    private DateTimeFormatter formatter;

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



        Map<Integer, Integer> payments = ledger.getPayments(now.truncatedTo(ChronoUnit.DAYS).minusDays(now.getDayOfMonth()-1).minusMonths(1));
        payments.keySet().forEach( day -> {
        });
        return true;
    }

    public void init(Ledger ledger, Config config) {
        ledgerStatsHistory.clear();
        ledgerHistoryTimestamps.clear();

        smallIntervalApproved = 0;
        bigIntervalApproved = 0;
        uptimeApproved = 0;

        bigInterval = config.getStatsIntervalBig();
        smallInterval = config.getStatsIntervalSmall();
        nodeStartTime = ZonedDateTime.now();
        lastStatsBuildTime = nodeStartTime;
        ledgerSize = ledger.getLedgerSize(null);

        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        builder.appendValue(ChronoField.DAY_OF_MONTH,2);
        builder.appendLiteral("/");
        builder.appendValue(ChronoField.MONTH_OF_YEAR,2);
        builder.appendLiteral("/");
        builder.appendValue(ChronoField.YEAR,4);
        formatter = builder.toFormatter();
    }

    public List<Binder> getPaymentStats(Ledger ledger, int daysNum) {
        List<Binder> result = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        Map<Integer, Integer> payments = ledger.getPayments(now.truncatedTo(ChronoUnit.DAYS).minusDays(daysNum));
        payments.keySet().forEach( day -> {
            result.add(Binder.of("date",ZonedDateTime.ofInstant(Instant.ofEpochSecond(day), ZoneId.systemDefault()).format(formatter), "units",payments.get(day)));
        });
        return result;
    }
}
