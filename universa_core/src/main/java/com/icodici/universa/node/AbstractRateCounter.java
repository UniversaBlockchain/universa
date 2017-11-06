/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node;

import java.io.Serializable;
import java.time.Duration;

/**
 * Limit something specifying and counting number of 'pulses' per some time slot. Limited are usages inside the slot,
 * when the slot is done, counts starts from 0.
 *
 * Serialization most completely save/restore its state: time slot, pulse limit, duration and pulses left
 */
public abstract class AbstractRateCounter implements Serializable {

    /**
     * Set the time stol to a specified period and start counting pulses. Current state os cleared.
     *
     * @param limit  maximum puleses per slot
     * @param period slot duration
     */
    public abstract void reset(int limit, Duration period);


    /**
     * The duration set by the {@link #reset(int, Duration)}
     * @return
     */
    public abstract Duration getDuration();

    /**
     * The pulse limit set by the {@link #reset(int, Duration)}
     * @return
     */
    public abstract int getPulseLimit();


    /**
     * Get the number of pulses left for the current time slot. If the slot is finished, does not create a new slot.
     *
     * @return number of pulses left in the current slot. Could be negative if the limited is exceeded.
     */
    public abstract int pulsesLeft();

    /**
     * Register a pulse. Creates new slot now and resets counter if the current slot is finished or there is no current
     * slot, what could happen after the reset.
     * <p>
     * Important. A new slot must always be created with this call. The time between the end of the current slot and the
     * first call to the {@link #countPulse()} does not count.
     *
     * @return true if the limit is not exceeded after counting the pulse.
     */
    public abstract boolean countPulse();

}

