package com.icodici.universa.contract.jsapi.permissions;

import com.icodici.universa.contract.jsapi.JSApiAccessor;
import com.icodici.universa.contract.permissions.Permission;

public abstract class JSApiPermission {

    abstract public Permission extractPermission(JSApiAccessor apiAccessor);

}
