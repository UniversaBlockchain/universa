package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.services.NSmartContract;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;

import java.math.BigDecimal;
import java.util.*;

public class NodeConfigProvider implements NSmartContract.NodeInfoProvider {

    private final Config config;

    public NodeConfigProvider() {
        config = new Config();
    }

    public NodeConfigProvider(Config config) {
        this.config = config;
    }

    @Override
    public Set<KeyAddress> getUIssuerKeys() {
        return config.getUIssuerKeys();
    }

    @Override
    public String getUIssuerName() {
        return config.getUIssuerName();
    }

    @Override
    public int getMinPayment(String extendedType) {
        return config.getMinPayment(extendedType);
    }

    @Override
    @Deprecated
    public double getRate(String extendedType) {
        return config.getRate(extendedType);
    }

    @Override
    public BigDecimal getServiceRate(String extendedType) {
        return config.getServiceRate(extendedType);
    }

    @Override
    public Collection<KeyAddress> getAdditionalKeysAddressesToSignWith(String extendedType) {
        Set<KeyAddress> set = new HashSet<>();
        if(extendedType.equals(NSmartContract.SmartContractType.UNS1)) {
            set.add(config.getAuthorizedNameServiceCenterAddress());
        }
        return set;
    }

    @Override
    public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
        throw new IllegalArgumentException("keys are not supported here. use key addresses instead");
    }
}
