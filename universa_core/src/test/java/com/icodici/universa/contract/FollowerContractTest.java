package com.icodici.universa.contract;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.services.FollowerContract;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.collections.Multimap;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.icodici.universa.contract.services.FollowerContract.FOLLOWER_ROLES_FIELD_NAME;
import static org.junit.Assert.*;

public class FollowerContractTest extends ContractTestBase {

    static Config nodeConfig;

    @BeforeClass
    public static void beforeClass() throws Exception {
        nodeConfig = new Config();
        nodeConfig.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
    }

    @Test
    public void goodFollowerContract() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        final PrivateKey key2 = new PrivateKey(Do.read(rootPath + "test_network_whitekey.private.unikey"));

        Contract simpleContract = new Contract(key2);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        PrivateKey privateKey = new PrivateKey(2048);
        PublicKey callbackKey = privateKey.getPublicKey();

        Contract smartContract = new FollowerContract(key);

        assertTrue(smartContract instanceof FollowerContract);

        ((FollowerContract)smartContract).setNodeInfoProvider(nodeInfoProvider);
        ((FollowerContract)smartContract).putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey);

        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), callbackKey );
        assertEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

        //updateCallbackKey

        PrivateKey newCallbackKey = new PrivateKey(2048);
        assertFalse(((FollowerContract) smartContract).updateCallbackKey("http://localhost:8888/follow.callback", newCallbackKey.getPublicKey()));
        assertTrue(((FollowerContract) smartContract).updateCallbackKey("http://localhost:7777/follow.callback", newCallbackKey.getPublicKey()));

        assertEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), newCallbackKey.getPublicKey());
        assertNotEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), callbackKey);

        assertEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

        //removeTrackingOrigin

        ((FollowerContract)smartContract).removeTrackingOrigin(simpleContract.getOrigin());

        assertNotEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), callbackKey );
        assertNotEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertFalse(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertFalse(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));


    }

    @Test
    public void goodSmartContractFromDSL() throws Exception {

        final PrivateKey key2 = new PrivateKey(Do.read(rootPath + "test_network_whitekey.private.unikey"));

        Contract simpleContract = new Contract(key2);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        PrivateKey privateKey = new PrivateKey(2048);
        PublicKey callbackKey = privateKey.getPublicKey();

        Contract smartContract = FollowerContract.fromDslFile(rootPath + "FollowerDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(rootPath + "_xer0yfe2nn1xthc.private.unikey");

        assertTrue(smartContract instanceof FollowerContract);

        ((FollowerContract)smartContract).setNodeInfoProvider(nodeInfoProvider);
        ((FollowerContract)smartContract).putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey);

        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"),callbackKey );
        assertEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

    }

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NSmartContract.NodeInfoProvider() {

        Config config = new Config();
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
        public double getRate(String extendedType) {
            return config.getRate(extendedType);
        }

        @Override
        public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
            Set<PublicKey> set = new HashSet<>();
            if(extendedType.equals(NSmartContract.SmartContractType.UNS1)) {
                set.add(config.getAuthorizedNameServiceCenterKey());
            }
            return set;
        }
    };

    @Test
    public void serializeSmartContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        final PrivateKey key2 = new PrivateKey(Do.read(rootPath + "test_network_whitekey.private.unikey"));

        Contract simpleContract = new Contract(key2);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        PrivateKey privateKey = new PrivateKey(2048);
        PublicKey callbackKey = privateKey.getPublicKey();

        Contract smartContract = new FollowerContract(key);

        assertTrue(smartContract instanceof FollowerContract);

        ((FollowerContract)smartContract).setNodeInfoProvider(nodeInfoProvider);

        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Binder b = BossBiMapper.serialize(smartContract);

        Contract desContract = DefaultBiMapper.deserialize(b);
        assertSameContracts(smartContract, desContract);

        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), desContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), desContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = desContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        ((FollowerContract)desContract).putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey);

        assertEquals(((FollowerContract) desContract).getCallbackKeys().get("http://localhost:7777/follow.callback"),callbackKey );
        assertEquals(((FollowerContract) desContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) desContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) desContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

        Contract copiedContract = smartContract.copy();
        assertSameContracts(smartContract, copiedContract);

        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), copiedContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), copiedContract.get("definition.extended_type"));

        assertTrue(copiedContract instanceof FollowerContract);

        permissions = desContract.getPermissions();
        mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        ((FollowerContract)copiedContract).putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey);

        assertEquals(((FollowerContract) copiedContract).getCallbackKeys().get("http://localhost:7777/follow.callback"),callbackKey );
        assertEquals(((FollowerContract) copiedContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) copiedContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) copiedContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

   }

    @Test
    public void followerContractNewRevision() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        final PrivateKey key2 = new PrivateKey(Do.read(rootPath + "test_network_whitekey.private.unikey"));

        Contract simpleContract = new Contract(key2);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        PrivateKey privateKey = new PrivateKey(2048);
        PublicKey callbackKey = privateKey.getPublicKey();

        Contract smartContract = new FollowerContract(key);

        assertTrue(smartContract instanceof FollowerContract);

        ((FollowerContract)smartContract).setNodeInfoProvider(nodeInfoProvider);
        ((FollowerContract)smartContract).putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey);

        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"),callbackKey );
        assertEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

        ////////////////////////

        Contract simpleContract2 = new Contract(key2);
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        FollowerContract newRevFollowerContract = (FollowerContract)smartContract.createRevision(key);

        assertTrue(newRevFollowerContract instanceof FollowerContract);

        newRevFollowerContract.putTrackingOrigin(simpleContract2.getOrigin(), "http://localhost:7777/follow.callbackTwo", callbackKey);
        newRevFollowerContract.setNodeInfoProvider(nodeInfoProvider);

        newRevFollowerContract.seal();
        newRevFollowerContract.check();
        newRevFollowerContract.traceErrors();
        assertTrue(newRevFollowerContract.isOk());

        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), newRevFollowerContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), newRevFollowerContract.get("definition.extended_type"));

        permissions = smartContract.getPermissions();
        mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(newRevFollowerContract.getCallbackKeys().get("http://localhost:7777/follow.callback"),callbackKey );
        assertEquals(newRevFollowerContract.getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(newRevFollowerContract.isOriginTracking(simpleContract.getOrigin()));
        assertTrue(newRevFollowerContract.isCallbackURLUsed("http://localhost:7777/follow.callback"));

        assertEquals(newRevFollowerContract.getCallbackKeys().get("http://localhost:7777/follow.callbackTwo"),callbackKey );
        assertEquals(newRevFollowerContract.getTrackingOrigins().get(simpleContract2.getOrigin()),
                "http://localhost:7777/follow.callbackTwo");
        assertTrue(newRevFollowerContract.isOriginTracking(simpleContract2.getOrigin()));
        assertTrue(newRevFollowerContract.isCallbackURLUsed("http://localhost:7777/follow.callbackTwo"));

        //updateCallbackKey

        PrivateKey newCallbackKey = new PrivateKey(2048);

        assertFalse(((FollowerContract) smartContract).updateCallbackKey("http://localhost:8888/follow.callback", newCallbackKey.getPublicKey()));
        assertTrue(((FollowerContract) smartContract).updateCallbackKey("http://localhost:7777/follow.callback", newCallbackKey.getPublicKey()));

        assertEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), newCallbackKey.getPublicKey());
        assertNotEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), callbackKey);

        assertEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

        assertEquals(newRevFollowerContract.getCallbackKeys().get("http://localhost:7777/follow.callbackTwo"),callbackKey );
        assertEquals(newRevFollowerContract.getTrackingOrigins().get(simpleContract2.getOrigin()),
                "http://localhost:7777/follow.callbackTwo");
        assertTrue(newRevFollowerContract.isOriginTracking(simpleContract2.getOrigin()));
        assertTrue(newRevFollowerContract.isCallbackURLUsed("http://localhost:7777/follow.callbackTwo"));

        //removeTrackingOrigin

        ((FollowerContract)smartContract).removeTrackingOrigin(simpleContract.getOrigin());

        assertNotEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"), callbackKey );
        assertNotEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertFalse(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertFalse(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));

        assertEquals(newRevFollowerContract.getCallbackKeys().get("http://localhost:7777/follow.callbackTwo"),callbackKey );
        assertEquals(newRevFollowerContract.getTrackingOrigins().get(simpleContract2.getOrigin()),
                "http://localhost:7777/follow.callbackTwo");
        assertTrue(newRevFollowerContract.isOriginTracking(simpleContract2.getOrigin()));
        assertTrue(newRevFollowerContract.isCallbackURLUsed("http://localhost:7777/follow.callbackTwo"));

    }

    @Test
    public void testCanFollowContract() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(rootPath + "_xer0yfe2nn1xthc.private.unikey"));
        final PrivateKey key2 = new PrivateKey(Do.read(rootPath + "test_network_whitekey.private.unikey"));

        Contract simpleContract = new Contract(key2);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        PrivateKey callbackKey = new PrivateKey(2048);

        Contract smartContract = new FollowerContract(key);

        assertTrue(smartContract instanceof FollowerContract);

        ((FollowerContract)smartContract).setNodeInfoProvider(nodeInfoProvider);
        ((FollowerContract)smartContract).putTrackingOrigin(simpleContract.getOrigin(), "http://localhost:7777/follow.callback", callbackKey.getPublicKey());

        // check canFollowContract

        // can not follow simpleContract (owner = key2) by smartContract (signed by key)
        assertFalse(((FollowerContract) smartContract).canFollowContract(simpleContract));

        Contract.Definition cd = simpleContract.getDefinition();

        List<Binder> newR = Do.listOf(smartContract.getRole("owner").serialize(new BiSerializer()));

        Binder data = new Binder();
        data.set(FOLLOWER_ROLES_FIELD_NAME, newR);
        cd.setData(data);

        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        assertTrue(((FollowerContract) smartContract).canFollowContract(simpleContract));

  /*    smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());


        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.FOLLOWER1.name(), smartContract.get("definition.extended_type"));

        Multimap<String, Permission> permissions = smartContract.getPermissions();
        Collection<Permission> mdp = permissions.get("modify_data");
        assertNotNull(mdp);
        assertTrue(((ModifyDataPermission)mdp.iterator().next()).getFields().containsKey("action"));

        assertEquals(((FollowerContract) smartContract).getCallbackKeys().get("http://localhost:7777/follow.callback"),callbackKey );
        assertEquals(((FollowerContract) smartContract).getTrackingOrigins().get(simpleContract.getOrigin()),
                "http://localhost:7777/follow.callback");
        assertTrue(((FollowerContract) smartContract).isOriginTracking(simpleContract.getOrigin()));
        assertTrue(((FollowerContract) smartContract).isCallbackURLUsed("http://localhost:7777/follow.callback"));*/







    }

}
