/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 */

package net.sergeych.tools;

/**
 * Memory and calculation-effectiver way to calculate average and standard deviation over arbitrary
 * number of samples. Memory usage does not depend on the number of samples, and all operations have
 * complexity of O(1).
 */
public class Average {

    private double sum = 0;
    private double sum2 = 0;
    private long n = 0;

    /**
     * add sample value
     *
     * @param value to add
     *
     * @return current sample length after adding this value
     */
    public long update(double value) {
        sum += value;
        sum2 += value * value;
        return ++n;
    }

    /**
     * @return average of samples
     */
    public double average() {
        if (n < 1)
            throw new IllegalStateException("too few samples");
        return sum / n;
    }

    /**
     * @return σ²(values)
     */
    public double stdev2() {
        double m = average();
        return sum2 / n - m * m;
    }

    /**
     * @return uncorrected sample standard deviation, √σ²
     */
    public double stdev() {
        return Math.sqrt(stdev2());
    }

    /**
     * @return number of samples
     */
    public long length() {
        return n;
    }

    /**
     * @return corrected sample standard deviation, e.g. √((N/(N-1))σ²)
     */
    public double correctedStdev() {
        long n = length();
        if (n < 2)
            throw new IllegalStateException("too few samples");
        return stdev() * Math.sqrt((double) n / (n - 1));
    }

    @Override
    public String toString() {
        return "" + average() + "±" + stdev();
    }
}
