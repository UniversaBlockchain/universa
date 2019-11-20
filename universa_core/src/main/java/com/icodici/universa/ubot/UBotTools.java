package com.icodici.universa.ubot;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.tools.Binder;

public class UBotTools {
    private static int getRequestPoolSizeFromMetadata(Binder methodBinder, Contract ubotRegistry) {
        Binder poolBinder = methodBinder.getBinderOrThrow("pool");

        // get UBot servers count
        Role ubotsRole = ubotRegistry.getRole("ubots");

        if (!(ubotsRole instanceof ListRole))
            throw new IllegalStateException("UBots role is not ListRole");

        int count = ((ListRole) ubotsRole).getRoles().size();
        if (count <= 0)
            throw new IllegalStateException("Incorrect UBots registry: no UBot servers");

        int poolSize = poolBinder.getInt("size", -1);
        if (poolSize > count || poolSize == 0)
            throw new IllegalStateException("Incorrect pool size");

        if (poolSize == -1) {
            int poolPercentage = poolBinder.getInt("percentage", -1);
            if (poolPercentage > 100 || poolPercentage == 0)
                throw new IllegalStateException("Incorrect pool percentage");

            if (poolPercentage == -1)
                throw new IllegalStateException("Pool size is not correctly specified as a constant (pool.size) or as a percentage (pool.percentage)");

            poolSize = (int) Math.ceil((double) count * poolPercentage / 100);
            if (poolSize == 0)
                throw new IllegalStateException("Pool size can`t be 0");
        }

        return poolSize;
    }

    public static int getRequestPoolSize(Contract requestContract, Contract ubotRegistry) {
        // get pool metadata
        HashId executableContractId = (HashId) requestContract.getStateData().get("executable_contract_id");
        String methodName = requestContract.getStateData().getString("method_name");
        Contract executableContract = requestContract.getTransactionPack().getReferencedItems().get(executableContractId);
        Binder methodBinder = executableContract.getStateData().getBinderOrThrow("cloud_methods").getBinderOrThrow(methodName);

        // get pool size
        return getRequestPoolSizeFromMetadata(methodBinder, ubotRegistry);
    }

    public static int getRequestQuorumSize(Contract requestContract, Contract ubotRegistry) {
        // get quorum metadata
        HashId executableContractId = (HashId) requestContract.getStateData().get("executable_contract_id");
        String methodName = requestContract.getStateData().getString("method_name");
        Contract executableContract = requestContract.getTransactionPack().getReferencedItems().get(executableContractId);
        Binder methodBinder = executableContract.getStateData().getBinderOrThrow("cloud_methods").getBinderOrThrow(methodName);
        Binder quorumBinder = methodBinder.getBinderOrThrow("quorum");

        // get pool size
        int poolSize = getRequestPoolSizeFromMetadata(methodBinder, ubotRegistry);

        // get quorum size
        int quorumSize = quorumBinder.getInt("size", -1);
        if (quorumSize > poolSize || quorumSize == 0)
            throw new IllegalStateException("Incorrect quorum size");

        if (quorumSize == -1) {
            int quorumPercentage = quorumBinder.getInt("percentage", -1);
            if (quorumPercentage > 100 || quorumPercentage == 0)
                throw new IllegalStateException("Incorrect quorum percentage");

            if (quorumPercentage == -1)
                throw new IllegalStateException("Quorum is not correctly specified as a constant (quorum.size) or as a percentage (quorum.percentage)");

            quorumSize = (int) Math.ceil((double) poolSize * quorumPercentage / 100);
            if (quorumSize == 0)
                throw new IllegalStateException("Quorum can`t be 0");
        }

        return quorumSize;
    }
}
