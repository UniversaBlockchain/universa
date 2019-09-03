package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.services.NSmartContract;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@BiType(name = "NodeConfigProvider")
public class NodeConfigProvider implements NSmartContract.NodeInfoProvider, BiSerializable {


    Config config;
    HashMap<String, BigDecimal> rate;
    Set<KeyAddress> issuerKeyAddresses;
    String issuerName;
    Map<String,Integer> minPayment;
    Map<String,Collection<KeyAddress>> additionalKeyAddresses;


    public NodeConfigProvider() {

    }

    public NodeConfigProvider(Config config) {
        this.config = config;

    }

    @Override
    public Set<KeyAddress> getUIssuerKeys() {
        if(config != null)
            return config.getUIssuerKeys();
        else
            return issuerKeyAddresses;
    }

    @Override
    public String getUIssuerName() {
        if(config != null)
            return config.getUIssuerName();
        else
            return issuerName;
    }

    @Override
    public int getMinPayment(String extendedType) {
        if(config != null)
            return config.getMinPayment(extendedType);
        else
            return minPayment.get(extendedType);
    }

    @Override
    @Deprecated
    public double getRate(String extendedType) {
        if(config != null)
            return config.getServiceRate(extendedType).doubleValue();
        else
            return rate.get(extendedType).doubleValue();
    }

    @Override
    public BigDecimal getServiceRate(String extendedType) {
        if(config != null)
            return config.getServiceRate(extendedType);
        else
            return rate.get(extendedType);
    }

    @Override
    public Collection<KeyAddress> getAdditionalKeysAddressesToSignWith(String extendedType) {
        if(config != null) {
            if(NSmartContract.SmartContractType.UNS1.name().equals(extendedType)) {
                return Do.listOf(config.getAuthorizedNameServiceCenterAddress());
            } else {
                return Do.listOf();
            }
        } else
            return additionalKeyAddresses.get(extendedType);
    }

    @Override
    public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
        throw new IllegalArgumentException("keys are not supported here. use key addresses instead");
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        issuerKeyAddresses = new HashSet<>(deserializer.deserialize(data.get("issuer_keys")));
        issuerName = data.getStringOrThrow("issuer_name");
        Map<String, String> r = deserializer.deserialize(data.get("rate"));
        rate = new HashMap<>();
        r.forEach((k,v) -> rate.put(k,new BigDecimal(v)));
        minPayment = deserializer.deserialize(data.get("min_payment"));
        additionalKeyAddresses = deserializer.deserialize(data.get("additional_keys"));

    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        if(config != null) {
            issuerKeyAddresses = new HashSet<>(config.getUIssuerKeys());
            issuerName = config.getUIssuerName();
            minPayment = new HashMap<>(config.minPayment);
            rate = new HashMap<>(config.rate);
            additionalKeyAddresses = new HashMap<>();
            additionalKeyAddresses.put(NSmartContract.SmartContractType.UNS1.name(), Do.listOf(config.getAuthorizedNameServiceCenterAddress()));

        }

        return Binder.of("issuer_keys",serializer.serialize(issuerKeyAddresses),
                "issuer_name",issuerName,
                "rate",serializer.serialize(rate),
                "min_payment",serializer.serialize(minPayment),
                "additional_keys",serializer.serialize(additionalKeyAddresses)
                );
    }

    static {
        DefaultBiMapper.registerClass(NodeConfigProvider.class);
    }
}
