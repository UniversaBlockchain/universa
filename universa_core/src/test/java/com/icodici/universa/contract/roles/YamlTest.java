/*
 * Created by Maxim Pogorelov <pogorelovm23@gmail.com>, 9/28/17.
 */

package com.icodici.universa.contract.roles;

import com.icodici.universa.TestKeys;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.util.*;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.*;


public class YamlTest {

    protected String rootPath = "./src/test_contracts/roles/";

    @Test
    public void createFromDsl() throws Exception {
        Yaml yaml = new Yaml();
        String fileName = rootPath + "simple_key.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getKeyRecords().size() == 1);
        }

        fileName = rootPath + "simple_keys.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getKeyRecords().size() == 2);
        }

        fileName = rootPath + "simple_no_type.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getKeyRecords().size() == 1);
        }

        fileName = rootPath + "simple_address.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getKeyAddresses().size() == 1);
        }

        fileName = rootPath + "simple_addresses.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getKeyAddresses().size() == 2);
        }

        fileName = rootPath + "simple_anonId.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getAnonymousIds().size() == 1);
        }

        fileName = rootPath + "simple_anonIds.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getAnonymousIds().size() == 2);
        }


        fileName = rootPath + "link.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof RoleLink);
        }

        fileName = rootPath + "list_all.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof ListRole);
        }

        fileName = rootPath + "list_any.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof ListRole);
        }

        fileName = rootPath + "list_quorum.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof ListRole);
        }

        fileName = rootPath + "simple_requires.yml";
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            Role role = Role.fromDslBinder("role",binder);
            assertTrue(role instanceof SimpleRole);
            assertTrue(role.getReferences(Role.RequiredMode.ALL_OF).size() == 3);
            assertTrue(role.getReferences(Role.RequiredMode.ANY_OF).size() == 2);
        }

    }

}
