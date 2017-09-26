/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.node.TestCase;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class ContractTestBase extends TestCase {
    protected String rootPath = "./src/test_contracts/";

    protected void assertProperSimpleRootContract(Contract c) {
        assertEquals(1, c.getApiLevel());

        // -- issuer
        KeyRecord issuer = c.getIssuer().getKeyRecords().iterator().next();
        assertNotNull(issuer);
        assertThat(issuer.getPublicKey(), is(instanceOf(PublicKey.class)));
        assertEquals(issuer.getStringOrThrow("name"), "Universa");

        // --- times
        ZonedDateTime t = c.getCreatedAt();
        assertAlmostSame(ZonedDateTime.now(), c.getCreatedAt());
        assertEquals("2022-08-05T17:25:37Z", c.getExpiresAt().withZoneSameInstant(ZoneOffset.UTC).toString());

        // -- data
        assertThat(c.getDefinition().getData().get("active_since"), is(instanceOf(ZonedDateTime.class)));
        assertEquals("2017-08-05T17:24:49Z", c.getDefinition()
                .getData()
                .getZonedDateTimeOrThrow("active_since")
                .withZoneSameInstant(ZoneOffset.UTC)
                .toString());
        assertEquals("access certificate", c.getDefinition().getData().get("type").toString());

        // -- state
        Contract.State state = c.getState();
        assertEquals(1, state.getRevision());
        assertEquals(c.getCreatedAt().truncatedTo(ChronoUnit.SECONDS),
                     state.getCreatedAt().truncatedTo(ChronoUnit.SECONDS));
        KeyRecord owner = c.testGetOwner();
        assertNotNull(owner);
        assertEquals("Pupkin", owner.getBinderOrThrow("name").getStringOrThrow("last"));

        // -- roles
        Map<String, Role> roles = c.getRoles();
        assertEquals(owner, roles.get("owner").getKeyRecord());
        assertEquals(issuer, roles.get("issuer").getKeyRecord());
        assertEquals(issuer, roles.get("creator").getKeyRecord());

        // -- permissions
        assertTrue(c.isPermitted("change_owner", owner));
        assertFalse(c.isPermitted("change_owner", issuer));
        assertTrue(c.isPermitted("revoke", owner));
        assertFalse(c.isPermitted("revoke", issuer));
    }
}
