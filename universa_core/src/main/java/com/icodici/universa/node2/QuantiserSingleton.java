package com.icodici.universa.node2;

import com.icodici.universa.HashId;

import java.util.HashMap;
import java.util.Map;

public class QuantiserSingleton {

    private static QuantiserSingleton ourInstance_s = new QuantiserSingleton();

    private Map<HashId,Quantiser> quantiserMap_ = new HashMap<>();



    private QuantiserSingleton() {
    }



    public synchronized static QuantiserSingleton getInstance() {
        return ourInstance_s;
    }



    public synchronized Quantiser getQuantiser(HashId hashId) {
        Quantiser q = quantiserMap_.get(hashId);
        if (q == null) {
            q = new Quantiser();
            quantiserMap_.put(hashId, q);
        }
        return q;
    }



    public synchronized void deleteQuantiser(HashId hashId) {
        quantiserMap_.remove(hashId);
    }



    public synchronized int getQuantiserCount() {
        return quantiserMap_.size();
    }

}
