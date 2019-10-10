package com.icodici.universa.ubot;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import net.sergeych.tools.Binder;

public class UBotTools {
    public static int getRequestQuorumSize(Contract requestContract, Contract ubotRegistry) {
        HashId executableContractId = (HashId) requestContract.getStateData().get("executable_contract_id");
        HashId requestId = requestContract.getId();

        String methodName = requestContract.getStateData().getString("method_name");
        Contract executableContract = requestContract.getTransactionPack().getReferencedItems().get(executableContractId);
        Binder methodBinder = executableContract.getStateData().getBinderOrThrow("cloud_methods").getBinderOrThrow(methodName);
        return methodBinder.getBinderOrThrow("quorum").getIntOrThrow("size");
    }
}
