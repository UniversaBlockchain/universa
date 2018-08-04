package com.icodici.universa.contract.jsapi.roles;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.roles.Role;

import java.util.List;

public abstract class JSApiRole {

    abstract List<String> getAllAddresses();

    abstract Role extractRole(JSApiAccessor apiAccessor);

}
