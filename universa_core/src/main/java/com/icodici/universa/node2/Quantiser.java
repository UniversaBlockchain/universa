package com.icodici.universa.node2;

public class Quantiser {

    private int quantaSum_ = 0;
    private int quantaLimit_ = -1;
    private boolean isCalculationFinished_ = false;

    public static final int PRICE_CHECK_2048_SIG       = 1;
    public static final int PRICE_CHECK_4096_SIG       = 8;
    public static final int PRICE_APPLICABLE_PERM      = 1;
    public static final int PRICE_SPLITJOIN_PERM       = 2;
    public static final int PRICE_REVOKE_VERSION       = 20;
    public static final int PRICE_REGISTER_VERSION     = 20;
    public static final int PRICE_CHECK_VERSION        = 1;




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



    public synchronized void addWorkCost(int price) throws QuantiserException {
        quantaSum_ += price;
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



    public class QuantiserException extends Exception {}

}
