package com.icodici.universa.contract.permissions;

import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.roles.RoleLink;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import static org.junit.Assert.*;

public class PermissionTest {

    @Test
    public void equality() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));

        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(new RoleLink("@owner","owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
            )
        );

        ChangeNumberPermission changeNumberPermission2 = new ChangeNumberPermission(new RoleLink("@owner","owner"), Binder.of(
                "field_name", "field1",
                "min_value", 34,
                "max_value", 36,
                "min_step", 1,
                "max_step", 2
        )
        );
        ChangeNumberPermission changeNumberPermission3 = new ChangeNumberPermission(new RoleLink("@owner","owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );


        ChangeNumberPermission changeNumberPermission4 = new ChangeNumberPermission(new RoleLink("@owner","issuer"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );


        assertEquals(changeNumberPermission,changeNumberPermission3);
        assertNotEquals(changeNumberPermission,changeNumberPermission2);
        assertNotEquals(changeNumberPermission,changeNumberPermission4);
        assertNotEquals(changeNumberPermission,"akjsfskgsf");
        assertNotEquals(changeNumberPermission,new ChangeOwnerPermission(new RoleLink("@owner","owner")));

    }

    @Test
    public void contractGetPermission() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        Contract contract2 = new Contract(TestKeys.privateKey(0));
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(new RoleLink("@owner", "owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );
        changeNumberPermission.setId("changenumber");
        contract.addPermission(changeNumberPermission);
        contract2.addPermission(changeNumberPermission);
        contract.seal();
        contract2.seal();

        contract = Contract.fromPackedTransaction(contract.getPackedTransaction());
        contract2 = Contract.fromPackedTransaction(contract2.getPackedTransaction());

        assertNotNull(contract.get("definition.permissions.changenumber"));
        assertEquals((Permission)contract.get("definition.permissions.changenumber"),contract2.get("definition.permissions.changenumber"));
    }


    @Test
    public void contractRefToPermission() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        Contract contract2 = new Contract(TestKeys.privateKey(0));
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(new RoleLink("@owner", "owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );

        ChangeNumberPermission changeNumberPermission2 = new ChangeNumberPermission(new RoleLink("@owner", "owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );
        changeNumberPermission.setId("changenumber");
        changeNumberPermission2.setId("changenumber2");

        contract.addPermission(changeNumberPermission);
        contract2.addPermission(changeNumberPermission2);
        contract.seal();


        Reference reference = new Reference(contract2);
        reference.name = "test1";
        reference.setConditions(Binder.of("all_of", Do.listOf("ref.id==\""+contract.getId().toBase64String()+"\"","this.definition.permissions.changenumber2==ref.definition.permissions.changenumber")));
        contract2.addReference(reference);

        contract2.seal();
        contract2.getTransactionPack().addReferencedItem(contract);
        contract2 = Contract.fromPackedTransaction(contract2.getPackedTransaction());
        assertTrue(contract2.check());
    }

}
