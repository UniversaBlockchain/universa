package com.icodici.universa.node2;

import com.icodici.universa.HashId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QuantiserSingleton {

    private static final QuantiserSingleton ourInstance_s = new QuantiserSingleton();

    private Map<HashId,Quantiser> quantiserMap_ = new ConcurrentHashMap<>();



    private QuantiserSingleton() {
    }



    public static QuantiserSingleton getInstance() {
        return ourInstance_s;
    }



    public Quantiser getQuantiser(HashId hashId) {
        quantiserMap_.putIfAbsent(hashId, new Quantiser());
        return quantiserMap_.get(hashId);
    }



    public void deleteQuantiser(HashId hashId) {
        quantiserMap_.remove(hashId);
    }



    public int getQuantiserCount() {
        return quantiserMap_.size();
    }

}
