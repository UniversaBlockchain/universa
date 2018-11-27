package com.icodici.universa.contract.roles;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.AnonymousId;
import com.icodici.universa.contract.KeyRecord;

import java.util.HashSet;
import java.util.Set;

public class RoleExtractor {

    public static Set<PublicKey> extractKeys(Role role) {
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleKeys();
        } else if(role instanceof RoleLink) {
            return extractKeys(role.resolve());
        } else if(role instanceof ListRole) {
            Set<PublicKey> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractKeys(r)));
            return result;
        }
        return null;
    }


    public static Set<AnonymousId> extractAnonymousIds(Role role) {
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleAnonymousIds();
        } else if(role instanceof RoleLink) {
            return extractAnonymousIds(role.resolve());
        } else if(role instanceof ListRole) {
            Set<AnonymousId> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractAnonymousIds(r)));
            return result;
        }
        return null;
    }


    public static Set<KeyAddress> extractKeyAddresses(Role role) {
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleKeyAddresses();
        } else if(role instanceof RoleLink) {
            return extractKeyAddresses(role.resolve());
        } else if(role instanceof ListRole) {
            Set<KeyAddress> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractKeyAddresses(r)));
            return result;
        }
        return null;
    }

    public static Set<KeyRecord> extractKeyRecords(Role role){
        if(role instanceof SimpleRole) {
            return ((SimpleRole) role).getSimpleKeyRecords();
        } else if(role instanceof RoleLink) {
            return extractKeyRecords(role.resolve());
        } else if(role instanceof ListRole) {
            Set<KeyRecord> result = new HashSet<>();
            ((ListRole) role).getRoles().forEach(r -> result.addAll(extractKeyRecords(r)));
            return result;
        }
        return null;
    }

}
