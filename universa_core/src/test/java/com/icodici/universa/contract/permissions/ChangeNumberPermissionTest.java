package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.TestKeys;
import net.sergeych.tools.Binder;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChangeNumberPermissionTest {

    @Test
    public void changeAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", 33);
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
            )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", 34);
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());
    }

    @Test
    public void changeDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", 33);
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 36,
                "min_step", 1,
                "max_step", 2
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", 36);
        changed.seal();
        changed.check();
        assertFalse(changed.isOk());
    }

    @Test
    public void changeAllowedStr() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 34,
                "min_step", 1,
                "max_step", 1
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "34");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());
    }

    @Test
    public void changeDeclinedStr() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", 33,
                "max_value", 36,
                "min_step", 1,
                "max_step", 2
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "36");
        changed.seal();
        changed.check();
        assertFalse(changed.isOk());
    }

    @Test
    public void changeAllowedStrDecimal() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33.000000000000000001");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", "33",
                "max_value", "34",
                "min_step", "0.000000000000000001",
                "max_step", "0.000000000000000001"
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "33.000000000000000002");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());
    }

    @Test
    public void changeDeclinedStrDecimal() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33.000000000000000001");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", "33",
                "max_value", "36",
                "min_step", "0.000000000000000001",
                "max_step", "0.000000000000000002"
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "33.000000000000000004");
        changed.seal();
        changed.check();
        assertFalse(changed.isOk());
    }

    @Test
    public void defaultParameters() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33.000000000000000001");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of("field_name", "field1"));
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "33.000000000000000002");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());
    }

    @Test
    public void lessMinValueDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33.000000000000000012");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", "33.000000000000000008",
                "max_value", "34",
                "min_step", "-0.000000000000000005",
                "max_step", "0.000000000000000005"
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "33.000000000000000007");
        changed.seal();
        changed.check();
        assertFalse(changed.isOk());
    }

    @Test
    public void greaterMaxValueDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33.000000000000000012");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", "33.000000000000000008",
                "max_value", "33.000000000000000016",
                "min_step", "-0.000000000000000005",
                "max_step", "0.000000000000000005"
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field1", "33.000000000000000017");
        changed.seal();
        changed.check();
        assertFalse(changed.isOk());
    }

    @Test
    public void incrementInt() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", "33",
                "max_value", "36",
                "min_step", "1",
                "max_step", "1"
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed1 = contract.createRevision();
        changed1.addSignerKey(TestKeys.privateKey(0));
        changed1.getStateData().set("field1", "34");
        changed1.seal();
        changed1.check();
        assertTrue(changed1.isOk());

        Contract changed2 = contract.createRevision();
        changed2.addSignerKey(TestKeys.privateKey(0));
        changed2.getStateData().set("field1", "34.000000000000000001");
        changed2.seal();
        changed2.check();
        assertFalse(changed2.isOk());

        Contract changed3 = contract.createRevision();
        changed3.addSignerKey(TestKeys.privateKey(0));
        changed3.getStateData().set("field1", "33.000000000000000001");
        changed3.seal();
        changed3.check();
        assertFalse(changed3.isOk());

        Contract changed4 = contract.createRevision();
        changed4.addSignerKey(TestKeys.privateKey(0));
        changed4.getStateData().set("field1", "33.1");
        changed4.seal();
        changed4.check();
        assertFalse(changed4.isOk());
    }

    @Test
    public void decrementInt() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field1", "33");
        ChangeNumberPermission changeNumberPermission = new ChangeNumberPermission(contract.getRole("owner"), Binder.of(
                "field_name", "field1",
                "min_value", "30",
                "max_value", "33",
                "min_step", "-1",
                "max_step", "-1"
        )
        );
        contract.addPermission(changeNumberPermission);
        contract.seal();

        Contract changed1 = contract.createRevision();
        changed1.addSignerKey(TestKeys.privateKey(0));
        changed1.getStateData().set("field1", "32");
        changed1.seal();
        changed1.check();
        assertTrue(changed1.isOk());

        Contract changed2 = contract.createRevision();
        changed2.addSignerKey(TestKeys.privateKey(0));
        changed2.getStateData().set("field1", "31.999999999999999999");
        changed2.seal();
        changed2.check();
        assertFalse(changed2.isOk());

        Contract changed3 = contract.createRevision();
        changed3.addSignerKey(TestKeys.privateKey(0));
        changed3.getStateData().set("field1", "32.000000000000000001");
        changed3.seal();
        changed3.check();
        assertFalse(changed3.isOk());

        Contract changed4 = contract.createRevision();
        changed4.addSignerKey(TestKeys.privateKey(0));
        changed4.getStateData().set("field1", "32.9");
        changed4.seal();
        changed4.check();
        assertFalse(changed4.isOk());
    }

}
