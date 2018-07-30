package com.icodici.universa.contract.jsapi;

import com.icodici.universa.contract.Contract;

/**
 * Implements js-api, that provided to client's javascript.
 */
public class JSApi {

    private Contract currentContract;

    public JSApi(Contract currentContract) {
        this.currentContract = currentContract;
    }

    public JSApiContract getCurrentContract() {
        return new JSApiContract(this.currentContract);
    }

}
