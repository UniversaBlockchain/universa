package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.InnerContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.FollowerCallback;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static com.icodici.universa.TestCase.assertAlmostSame;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class SlotMainTest extends BaseMainTest {
    @Test
    public void testSlotApi() throws Exception {
        List<Main> mm = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            mm.add(createMain("node" + (i + 1), false));
        Main main = mm.get(0);
        main.config.setIsFreeRegistrationsAllowedFromYaml(true);
        Client client = new Client(TestKeys.privateKey(20), main.myInfo, null);

        Decimal kilobytesAndDaysPerU = client.storageGetRate();
        System.out.println("storageGetRate: " + kilobytesAndDaysPerU);
        assertEquals(new Decimal(main.config.getServiceRate("SLOT1").toString()), kilobytesAndDaysPerU);

        Contract simpleContract = new Contract(TestKeys.privateKey(1));
        simpleContract.seal();
        ItemResult itemResult = client.register(simpleContract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        SlotContract slotContract = ContractsService.createSlotContract(new HashSet<>(asList(TestKeys.privateKey(1))), new HashSet<>(asList(TestKeys.publicKey(1))), nodeInfoProvider);
        slotContract.setNodeInfoProvider(nodeInfoProvider);
        slotContract.putTrackingContract(simpleContract);

        Contract stepaU = InnerContractsService.createFreshU(100000000, new HashSet<>(asList(TestKeys.publicKey(1))));
        itemResult = client.register(stepaU.getPackedTransaction(), 5000);
        System.out.println("stepaU : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        Parcel parcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), stepaU, 1, 100, new HashSet<>(asList(TestKeys.privateKey(1))), false);

        Binder slotInfo = client.querySlotInfo(slotContract.getId());
        System.out.println("slot info is null: " + (slotInfo == null));
        assertNull(slotInfo);

        byte[] simpleContractBytes = client.queryContract(slotContract.getId(), null, simpleContract.getId());
        System.out.println("simpleContractBytes (by contractId): " + simpleContractBytes);
        assertEquals(false, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        simpleContractBytes = client.queryContract(slotContract.getId(), simpleContract.getOrigin(), null);
        System.out.println("simpleContractBytes (by originId): " + simpleContractBytes);
        assertEquals(false, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        client.registerParcelWithState(parcel.pack(), 5000);
        itemResult = client.getState(slotContract.getId());
        System.out.println("slot : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        slotInfo = client.querySlotInfo(slotContract.getId());
        System.out.println("slot info size: " + slotInfo.size());
        assertNotNull(slotInfo);

        simpleContractBytes = client.queryContract(slotContract.getId(), null, simpleContract.getId());
        System.out.println("simpleContractBytes (by contractId) length: " + simpleContractBytes.length);
        assertEquals(true, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        simpleContractBytes = client.queryContract(slotContract.getId(), simpleContract.getOrigin(), null);
        System.out.println("simpleContractBytes (by originId) length: " + simpleContractBytes.length);
        assertEquals(true, Arrays.equals(simpleContract.getPackedTransaction(), simpleContractBytes));

        mm.forEach(x -> x.shutdown());

    }
    
}
