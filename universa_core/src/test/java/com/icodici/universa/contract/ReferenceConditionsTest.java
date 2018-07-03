package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.List;

import static com.icodici.universa.contract.Reference.conditionsModeType.all_of;
import static java.util.Arrays.asList;
import static org.junit.Assert.*;

public class ReferenceConditionsTest {
    protected static final String ROOT_PATH = "./src/test_contracts/references/";

    @Test
    public void checkReferences() throws Exception {

        Contract contract1 = Contract.fromDslFile(ROOT_PATH + "ReferencedConditions_contract1.yml");
        Contract contract2 = Contract.fromDslFile(ROOT_PATH + "ReferencedConditions_contract2.yml");

        PrivateKey key = new PrivateKey(Do.read("./src/test_contracts/" + "_xer0yfe2nn1xthc.private.unikey"));

        Binder conditions = contract1.getReferences().get("ref_string").getConditions();
        List<Object> condList = conditions.getList(all_of.name(), null);

        // Mirroring conditions with strings
        condList.add("\"string\"!=ref.state.data.string3");
        condList.add("\"==INFORMATION==\"==ref.definition.data.string2");
        condList.add("\"26RzRJDLqze3P5Z1AzpnucF75RLi1oa6jqBaDh8MJ3XmTaUoF8R\"==ref.definition.issuer");
        condList.add("\"mqIooBcuyMBRLHZGJGQ7osf6TnoWkkVVBGNG0LDuPiZeXahnDxM+PoPMgEuqzOvsfoWNISyqYaCYyR9" +
                "zCfpZCF6pjZ+HvjsD73pZ6uaXlUY0e72nBPNbAtFhk2pEXyxt\"!= this.id");
        condList.add("\"HggcAQABxAACzHE9ibWlnK4RzpgFIB4jIg3WcXZSKXNAqOTYUtGXY03xJSwpqE+y/HbqqE0WsmcAt5\n" +
                "           a0F5H7bz87Uy8Me1UdIDcOJgP8HMF2M0I/kkT6d59ZhYH/TlpDcpLvnJWElZAfOytaICE01bkOkf6M\n" +
                "           z5egpToDEEPZH/RXigj9wkSXkk43WZSxVY5f2zaVmibUZ9VLoJlmjNTZ+utJUZi66iu9e0SXupOr/+\n" +
                "           BJL1Gm595w32Fd0141kBvAHYDHz2K3x4m1oFAcElJ83ahSl1u85/naIaf2yuxiQNz3uFMTn0IpULCM\n" +
                "           vLMvmE+L9io7+KWXld2usujMXI1ycDRw85h6IJlPcKHVQKnJ/4wNBUveBDLFLlOcMpCzWlO/D7M2Iy\n" +
                "           Na8XEvwPaFJlN1UN/9eVpaRUBEfDq6zi+RC8MaVWzFbNi913suY0Q8F7ejKR6aQvQPuNN6bK6iRYZc\n" +
                "           hxe/FwWIXOr0C0yA3NFgxKLiKZjkd5eJ84GLy+iD00Rzjom+GG4FDQKr2HxYZDdDuLE4PEpYSzEB/8\n" +
                "           LyIqeM7dSyaHFTBII/sLuFru6ffoKxBNk/cwAGZqOwD3fkJjNq1R3h6QylWXI/cSO9yRnRMmMBJwal\n" +
                "           MexOc3/kPEEdfjH/GcJU0Mw6DgoY8QgfaNwXcFbBUvf3TwZ5Mysf21OLHH13g8gzREm+h8c=\"==ref.definition.issuer");
        condList.add("\"1:25\"==this.state.branchId");
        contract1.getReferences().get("ref_string").setConditions(conditions);

        conditions = contract1.getReferences().get("ref_time").getConditions();
        condList = conditions.getList(all_of.name(), null);

        // Mirroring conditions with time string
        condList.add("\"1977-06-14 16:03:10\"<ref.definition.created_at");
        condList.add("\"2958-04-18 00:58:00\">this.definition.expires_at");

        contract1.getReferences().get("ref_time").setConditions(conditions);

        contract2.seal();

        Contract contract3 = contract2.createRevision(key);
        contract3.seal();

        contract1.getStateData().set("contract2_origin", contract2.getOrigin().toBase64String());
        contract1.getStateData().set("contract2_id", contract2.getId().toBase64String());
        contract1.getStateData().set("contract3_parent", contract3.getParent().toBase64String());

        contract1.getState().setBranchNumber(25);
        System.out.println("branchId: " + contract1.getState().getBranchId());
        contract1.seal();

        System.out.println("Contract3_parent: " + contract3.getParent().toBase64String());
        System.out.println("contract2_origin:  " + contract1.getStateData().get("contract2_origin"));
        System.out.println("contract2_id:  " + contract1.getStateData().get("contract2_id"));

        TransactionPack tp = new TransactionPack();
        tp.setContract(contract1);
        tp.addSubItem(contract2);
        tp.addReferencedItem(contract2);
        tp.addSubItem(contract3);
        tp.addReferencedItem(contract3);

        Contract refContract = new Contract(contract1.seal(), tp);
        refContract.check();

        System.out.println("Check roles conditions");
        assertTrue(refContract.getReferences().get("ref_roles").matchingItems.contains(contract2));
        System.out.println("Check integer conditions");
        assertTrue(refContract.getReferences().get("ref_integer").matchingItems.contains(contract2));
        System.out.println("Check float conditions");
        assertTrue(refContract.getReferences().get("ref_float").matchingItems.contains(contract2));
        System.out.println("Check string conditions");
        assertTrue(refContract.getReferences().get("ref_string").matchingItems.contains(contract2));
        System.out.println("Check boolean conditions");
        assertTrue(refContract.getReferences().get("ref_boolean").matchingItems.contains(contract2));
        System.out.println("Check inherited conditions");
        assertTrue(refContract.getReferences().get("ref_inherited").matchingItems.contains(contract2));
        System.out.println("Check time conditions");
        assertTrue(refContract.getReferences().get("ref_time").matchingItems.contains(contract2));
        System.out.println("Check ref_hashes conditions");
        assertTrue(refContract.getReferences().get("ref_hashes").matchingItems.contains(contract2));
        System.out.println("Check parent conditions");
        assertTrue(refContract.getReferences().get("ref_parent").matchingItems.contains(contract3));
     }

    @Test
    public void refLessOrEquals() throws Exception {
        Contract contractA = new Contract(new PrivateKey(2048));
        contractA.getStateData().put("val", 100);

        Contract contractB = new Contract(new PrivateKey(2048));
        Reference ref = new Reference();
        ref.type = Reference.TYPE_EXISTING_STATE;
        ref.setConditions(Binder.of(
                Reference.conditionsModeType.all_of.name(),
                asList("ref.state.data.val<10")
        ));
        contractB.addReference(ref);

        Contract batch = new Contract(new PrivateKey(2048));
        batch.addNewItems(contractA);
        batch.addNewItems(contractB);
        batch.seal();
        Boolean res = batch.check();
        batch.traceErrors();
        assertEquals(false, res);
    }

    @Test
    public void refMissingField() throws Exception {
        Contract contractA = new Contract(new PrivateKey(2048));
        contractA.getStateData().put("val", 100);

        Contract contractB = new Contract(new PrivateKey(2048));
        Reference ref = new Reference();
        ref.type = Reference.TYPE_EXISTING_STATE;
        ref.setConditions(Binder.of(
                Reference.conditionsModeType.all_of.name(),
                asList("ref.state.data.val>90")
        ));
        contractB.addReference(ref);

        Contract batch = new Contract(new PrivateKey(2048));
        batch.addNewItems(contractA);
        batch.addNewItems(contractB);
        batch.seal();
        Boolean res = batch.check();
        batch.traceErrors();
        assertEquals(true, res);

        contractA.getStateData().remove("val");
        batch.seal();
        res = batch.check();
        batch.traceErrors();
        assertEquals(false, res);
    }

}