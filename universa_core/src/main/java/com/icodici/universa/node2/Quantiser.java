package com.icodici.universa.node2;

import java.io.IOException;

public class Quantiser {

    private int quantaSum_ = 0;
    private int quantaLimit_ = -1;
    private boolean isCalculationFinished_ = false;


    public Quantiser() {
    }



    public synchronized void reset(int newLimit) {
        quantaSum_ = 0;
        quantaLimit_ = newLimit;
        isCalculationFinished_ = false;
    }



    public synchronized void resetNoLimit() {
        reset(-1);
    }



    public synchronized void addWorkCost(QuantiserProcesses process) throws QuantiserException {
        quantaSum_ += process.getCost();
//        System.out.println("Add processing cost for " + process + " (" + process.getCost() + "), now cost is " + quantaSum_);
        if (quantaLimit_ >= 0)
            if (quantaSum_ > quantaLimit_)
                throw new QuantiserException();
    }



    public synchronized void addWorkCostFrom(Quantiser quantiser) throws QuantiserException {
        quantaSum_ += quantiser.getQuantaSum();
//        System.out.println("Add processing cost from " + quantiser.getClass().getSimpleName() + " (" + quantiser.getQuantaSum() + "), now cost is " + quantaSum_);
        if (quantaLimit_ >= 0)
            if (quantaSum_ > quantaLimit_)
                throw new QuantiserException();
    }



    public synchronized int getQuantaSum() {
        return quantaSum_;
    }



    public synchronized int getQuantaLimit() {
        return quantaLimit_;
    }



    public synchronized boolean isCalculationFinished() {return isCalculationFinished_;}



    public synchronized void finishCalculation() {isCalculationFinished_ = true;}


    public enum QuantiserProcesses {

        PRICE_CHECK_2048_SIG,
        PRICE_CHECK_4096_SIG,
        PRICE_APPLICABLE_PERM,
        PRICE_SPLITJOIN_PERM,
        PRICE_REVOKE_VERSION,
        PRICE_REGISTER_VERSION,
        PRICE_CHECK_REFERENCED_VERSION;

        /**
         * Return cost of the process.
         *
         * @return
         */
        public int getCost() {
            switch (this) {
                case PRICE_CHECK_2048_SIG:
                    return 1;

                case PRICE_CHECK_4096_SIG:
                    return 8;

                case PRICE_APPLICABLE_PERM:
                    return 1;

                case PRICE_SPLITJOIN_PERM:
                    return 2;

                case PRICE_REGISTER_VERSION:
                    return 20;

                case PRICE_REVOKE_VERSION:
                    return 20;

                case PRICE_CHECK_REFERENCED_VERSION:
                    return 1;
            }
            return 0;
        }
    }

    public class QuantiserException extends IOException {}

}
