package com.icodici.universa.contract;

public class ReferenceRole {
    public String name;
    public byte[] fingerprint;

    public ReferenceRole(String name, byte[] fingerprint) {
        this.name = name;
        this.fingerprint = fingerprint;
    }
}
