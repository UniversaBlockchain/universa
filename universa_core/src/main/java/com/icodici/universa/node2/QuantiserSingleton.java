package com.icodici.universa.node2;

public class QuantiserSingleton {

    private static QuantiserSingleton ourInstance_s = new QuantiserSingleton();
    private Quantiser quantiser_ = new Quantiser(100);



    private QuantiserSingleton() {
    }

    public static QuantiserSingleton getInstance() {
        return ourInstance_s;
    }

    public Quantiser getQuantiser() {
        return quantiser_;
    }

}
