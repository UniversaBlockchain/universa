package com.icodici.universa.node;

import java.io.Serializable;
import java.time.Duration;

/**
 * @exclude not yet used
 */
public class RateCounter extends AbstractRateCounter {

    private int limit;
    private Duration period;
    private TimeSlot currentTimeSlot;

    public RateCounter (int limit, Duration period) {
        reset(limit, period);
    }

    @Override
    public void reset(int limit, Duration period) {
        this.limit = limit;
        this.period = period;
    }

    @Override
    public Duration getDuration() {
        return period;
    }

    @Override
    public int getPulseLimit() {
        return limit;
    }

    @Override
    public int pulsesLeft() {
        if(currentTimeSlot != null && currentTimeSlot.isActive())
            return currentTimeSlot.limit - currentTimeSlot.currentCount;

        return limit;
    }

    public long millisecondsLeft() {
        if(currentTimeSlot != null && currentTimeSlot.isActive())
            return currentTimeSlot.millisecondsLeft();

        return 0;
    }

    @Override
    public boolean countPulse() {
        if(currentTimeSlot != null && currentTimeSlot.isActive()) {
        } else {
            currentTimeSlot = new TimeSlot(limit, period);
        }

        return currentTimeSlot.countPulse();
    }

    /**
     * @exclude
     */
    public class TimeSlot implements Serializable {

        private int limit;
        private int currentCount;
        private long startTime;
        private Duration period;

        public TimeSlot(int limit, Duration period) {
            System.out.println("creates new time slot ");
            this.startTime = System.currentTimeMillis();
            this.limit = limit;
            this.period = period;
        }

        public boolean countPulse() {
            currentCount++;
            if(currentCount > limit)
                return false;

            return true;
        }

        public boolean isActive() {
            return System.currentTimeMillis() - startTime <= period.toMillis();
        }

        public long millisecondsLeft() {
            if(isActive())
                return (startTime + period.toMillis()) - System.currentTimeMillis();

            return 0;
        }
    }
}
