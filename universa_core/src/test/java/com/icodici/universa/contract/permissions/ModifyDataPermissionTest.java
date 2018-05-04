package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTestBase;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ModifyDataPermissionTest extends TestCase {

    @Test
    public void modifyDataAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("field_to_be_changed", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value2");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());
    }

    @Test
    public void modifyDataDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value2");
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());
    }

    @Test
    public void modifyDataWhiteList() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("field_to_be_changed", Do.listOf("value2", "value3"));
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value2");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value3");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value4");
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());
    }

    @Test
    public void modifyExpiresAtAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("/expires_at", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(ZonedDateTime.now().plusDays(1));
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

    }

    @Test
    public void modifyExpiresAtDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("expires_at", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(ZonedDateTime.now().plusDays(1));
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());

    }

    @Test
    public void modifyExpiresAtWhiteList() throws Exception {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        Contract contract = new Contract(TestKeys.privateKey(0));
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("/expires_at", Do.listOf(now.plusDays(1), now.plusDays(2)));
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(now.plusDays(1));
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(now.plusDays(2));
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(now.plusDays(3));
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());
    }

    @Test
    public void modifyReferencesAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"),new Binder());
        modifyDataPermission.addField("/references", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract referencedContract = new Contract(TestKeys.privateKey(1));
        referencedContract.seal();
        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        Reference ref = new Reference(referencedContract);
        ref.addMatchingItem(referencedContract);
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

    }

    @Test
    public void modifyReferencesDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"),new Binder());
        modifyDataPermission.addField("references", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract referencedContract = new Contract(TestKeys.privateKey(1));
        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        Reference ref = new Reference(referencedContract);
        ref.addMatchingItem(referencedContract);
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());

    }

    @Test
    public void modifyReferencesWhiteList() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"),new Binder());
        Contract referencedContract = new Contract(TestKeys.privateKey(1));

        List<String> listConditionsForDefinition = new ArrayList<>();
        listConditionsForDefinition.add("ref.definition.data.type == \"Good Bank\"");
        Binder conditionsForDefinition = new Binder();
        conditionsForDefinition.set("all_of", listConditionsForDefinition);

        Reference ref = new Reference(referencedContract);
        ref.name = "bank_certificate";
        ref.type = Reference.TYPE_EXISTING;
        ref.setConditions(conditionsForDefinition);
        ref.addMatchingItem(referencedContract);

        modifyDataPermission.addField("/references", Do.listOf(ref));
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertTrue(changed.isOk());

        // we allow setting any contract matching conditions
//        referencedContract = new Contract(TestKeys.privateKey(2));
//        ref = new Reference(referencedContract);
//        ref.addMatchingItem(referencedContract);

        // but dissallow change reference and its conditions itself
        ref.name = "stepa_certificate";

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertFalse(changed.isOk());

        ref.name = "bank_certificate";
        ref.type = Reference.TYPE_TRANSACTIONAL;

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertFalse(changed.isOk());

        ref.type = Reference.TYPE_EXISTING;
        conditionsForDefinition = new Binder();
        conditionsForDefinition.set("any_of", listConditionsForDefinition);
        ref.setConditions(conditionsForDefinition);

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertFalse(changed.isOk());

        listConditionsForDefinition = new ArrayList<>();
        listConditionsForDefinition.add("ref.definition.data.type == \"Stepa Bank\"");
        conditionsForDefinition = new Binder();
        conditionsForDefinition.set("all_of", listConditionsForDefinition);
        ref.setConditions(conditionsForDefinition);

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertFalse(changed.isOk());

        // and check that backed values is ok

        listConditionsForDefinition = new ArrayList<>();
        listConditionsForDefinition.add("ref.definition.data.type == \"Good Bank\"");
        conditionsForDefinition = new Binder();
        conditionsForDefinition.set("all_of", listConditionsForDefinition);
        ref.setConditions(conditionsForDefinition);

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.addReferenceToState(ref);
        changed.seal();
        changed.check();
        changed.traceErrors();
        assertTrue(changed.isOk());

    }


}
