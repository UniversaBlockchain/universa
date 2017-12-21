package com.icodici.universa.contract;

import com.icodici.universa.HashId;

public class ReferenceModel {
    public String name;
    public short type;
    public String transactional_id;
    public HashId contract_id;
    public boolean required;
    public HashId origin;
};
